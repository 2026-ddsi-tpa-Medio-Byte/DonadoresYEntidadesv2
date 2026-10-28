package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.MetricasService;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.clients.LogisticaStockClient;
import ar.edu.utn.dds.k3003.logging.TrazabilidadFilter;
import ar.edu.utn.dds.k3003.repositories.InMemoryDonadoresRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryEntidadesRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryNecesidadesRepo;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitOperations;

/**
 * Cada operación de la fachada publica el evento que dice {@code CONTRATO-EVENTOS.md}.
 *
 * <p>Corre con repositorios en memoria y un RabbitTemplate simulado, y mira el mensaje tal como
 * sale: exchange, routing key, propiedades y el JSON. El evento de aplicación se le entrega directo
 * al publicador, como pasa en las operaciones sin transacción propia (el caso del
 * {@code fallbackExecution}); el pasaje por el commit se prueba con Spring en
 * {@link EventosIntegrationTest}.
 */
class EventosPorOperacionTest {

  /** UTC, con milisegundos y Z: el formato del contrato. */
  private static final Pattern FECHA_UTC =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");

  private static final Set<String> CAMPOS_SOBRE =
      Set.of("eventoId", "tipo", "modulo", "entidadId", "ocurridoEn", "datos");

  private InMemoryNecesidadesRepo necesidades;
  private SimpleMeterRegistry registro;
  private MetricasService metricas;
  private RabbitOperations rabbit;
  private MensajesCapturados rabbitMQ;
  private Fachada fachada;

  @BeforeEach
  void setUp() {
    necesidades = new InMemoryNecesidadesRepo();
    registro = new SimpleMeterRegistry();
    metricas = new MetricasService(registro);
    fachada =
        new Fachada(new InMemoryDonadoresRepo(), new InMemoryEntidadesRepo(), necesidades, metricas);
    rabbit = mock(RabbitOperations.class);
    rabbitMQ = MensajesCapturados.en(rabbit);
    conectarPublicador(rabbit);
  }

  @AfterEach
  void limpiarMdc() {
    MDC.clear();
  }

  private void conectarPublicador(RabbitOperations conQue) {
    PublicadorEventos publicador = new PublicadorEventos(conQue, "amqp://localhost", metricas);
    fachada.setEventos(new EventosDonadores(e -> publicador.alConfirmarse((EventoDonadores) e)));
  }

  private String crearDonador() {
    return fachada
        .agregarDonador(
            new DonadorDTO(
                null, "Ana", "Pérez", 30, "ana@mail.com", "30111222", "Calle Falsa 123", null,
                null))
        .id();
  }

  private String crearEntidad() {
    return fachada
        .agregarEntidad(
            new EntidadBeneficaDTO(
                null, "Comedor Los Pibes", "Av. Siempreviva 742", "11-4444-5555",
                "comedor@mail.com"))
        .id();
  }

  private String crearNecesidad(int objetivo, String productoID, TipoNecesidadMaterialEnum tipo) {
    String entidadID = crearEntidad();
    return fachada
        .registrarNecesidad(
            new NecesidadMaterialDTO(null, entidadID, 7, "frazadas", objetivo, productoID, tipo))
        .id();
  }

  private QuejaDTO queja(String donadorID, String donacionID) {
    return new QuejaDTO(null, donacionID, donadorID, LocalDate.of(2026, 10, 26), "llegó roto");
  }

  /** Lo común a todos los eventos: a dónde fue, el sobre completo y cómo se mandó. */
  private void verificarSobre(
      MensajesCapturados.Enviado enviado, TipoEvento tipo, String entidadId) {
    assertEquals("donatrack.eventos", enviado.exchange());
    assertEquals(tipo.routingKey(), enviado.routingKey());
    assertEquals(CAMPOS_SOBRE, MensajesCapturados.campos(enviado.sobre()));
    String eventoId = enviado.sobre().get("eventoId").asText();
    assertEquals(eventoId, UUID.fromString(eventoId).toString(), "eventoId tiene que ser un UUID");
    assertEquals(tipo.name(), enviado.tipo());
    assertEquals("donadores", enviado.sobre().get("modulo").asText());
    assertEquals(entidadId, enviado.entidadId());
    assertTrue(FECHA_UTC.matcher(enviado.ocurridoEn()).matches(), enviado.ocurridoEn());
    assertEquals("application/json", enviado.mensaje().getMessageProperties().getContentType());
    assertEquals(
        MessageDeliveryMode.PERSISTENT,
        enviado.mensaje().getMessageProperties().getDeliveryMode());
  }

  @Nested
  @DisplayName("Donadores")
  class Donadores {

    @Test
    @DisplayName("El alta publica DONADOR_CREADO con el sobre y los datos completos")
    void alta() {
      String id = crearDonador();

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.DONADOR_CREADO, id);
      JsonNode datos = enviado.datos();
      assertEquals(
          Set.of(
              "id", "nombre", "apellido", "estado", "categoria", "historialCategorias",
              "cantidadQuejas"),
          MensajesCapturados.campos(datos));
      assertEquals(id, datos.get("id").asText());
      assertEquals("Ana", datos.get("nombre").asText());
      assertEquals("Pérez", datos.get("apellido").asText());
      assertEquals("VERIFICADO", datos.get("estado").asText());
      assertEquals("Colaborador", datos.get("categoria").asText());
      assertEquals(0, datos.get("cantidadQuejas").asInt());
      JsonNode historial = datos.get("historialCategorias");
      assertEquals(1, historial.size());
      assertEquals(Set.of("categoria", "desde"), MensajesCapturados.campos(historial.get(0)));
      assertEquals("Colaborador", historial.get(0).get("categoria").asText());
      assertTrue(FECHA_UTC.matcher(historial.get(0).get("desde").asText()).matches());
    }

    @Test
    @DisplayName("Ningún evento del donador lleva email, documento, domicilio ni edad")
    void sinDatosPersonales() {
      String id = crearDonador();
      fachada.modificarEstado(id, EstadoDonadorEnum.SOSPECHOSO);
      fachada.modifcarCategoria(id, "Oro");
      fachada.agregarQueja(queja(id, "donacion1"));

      assertEquals(
          List.of("DONADOR_CREADO", "DONADOR_MODIFICADO", "DONADOR_MODIFICADO", "QUEJA_REGISTRADA",
              "DONADOR_MODIFICADO"),
          rabbitMQ.tipos());
      for (MensajesCapturados.Enviado enviado : rabbitMQ.todos()) {
        String cuerpo = enviado.cuerpo();
        for (String prohibido :
            List.of("email", "nroDocumento", "domicilio", "edad", "ana@mail.com", "30111222",
                "Calle Falsa 123")) {
          assertFalse(cuerpo.contains(prohibido), enviado.tipo() + " lleva " + prohibido);
        }
      }
    }

    @Test
    @DisplayName("Un cambio de estado a mano publica DONADOR_MODIFICADO con el estado nuevo")
    void cambioDeEstado() {
      String id = crearDonador();
      rabbitMQ.limpiar();

      fachada.modificarEstado(id, EstadoDonadorEnum.BANEADO);

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.DONADOR_MODIFICADO, id);
      assertEquals("BANEADO", enviado.datos().get("estado").asText());
    }

    @Test
    @DisplayName("Un cambio de categoría publica DONADOR_MODIFICADO con el historial completo")
    void cambioDeCategoria() {
      String id = crearDonador();
      rabbitMQ.limpiar();

      fachada.modifcarCategoria(id, "Oro");

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.DONADOR_MODIFICADO, id);
      assertEquals("Oro", enviado.datos().get("categoria").asText());
      JsonNode historial = enviado.datos().get("historialCategorias");
      assertEquals(2, historial.size());
      assertEquals("Colaborador", historial.get(0).get("categoria").asText());
      assertEquals("Oro", historial.get(1).get("categoria").asText());
      assertTrue(
          historial.get(0).get("desde").asText().compareTo(historial.get(1).get("desde").asText())
              <= 0,
          "el historial va de la más vieja a la actual");
    }

    @Test
    @DisplayName("Si la categoría no cambia no se publica nada")
    void mismaCategoria() {
      String id = crearDonador();
      rabbitMQ.limpiar();

      fachada.modifcarCategoria(id, "Colaborador");

      assertTrue(rabbitMQ.todos().isEmpty(), rabbitMQ.tipos().toString());
    }
  }

  @Nested
  @DisplayName("Quejas")
  class Quejas {

    @Test
    @DisplayName("Una queja publica QUEJA_REGISTRADA y después DONADOR_MODIFICADO con una queja más")
    void quejaYDonador() {
      String donadorID = crearDonador();
      rabbitMQ.limpiar();

      QuejaDTO registrada = fachada.agregarQueja(queja(donadorID, "donacion7"));

      assertEquals(List.of("QUEJA_REGISTRADA", "DONADOR_MODIFICADO"), rabbitMQ.tipos());
      MensajesCapturados.Enviado deLaQueja = rabbitMQ.todos().get(0);
      verificarSobre(deLaQueja, TipoEvento.QUEJA_REGISTRADA, registrada.id());
      JsonNode datos = deLaQueja.datos();
      assertEquals(
          Set.of("id", "donacionID", "donadorID", "fecha", "descripcion"),
          MensajesCapturados.campos(datos));
      assertEquals(registrada.id(), datos.get("id").asText());
      assertEquals("donacion7", datos.get("donacionID").asText());
      assertEquals(donadorID, datos.get("donadorID").asText());
      assertEquals("llegó roto", datos.get("descripcion").asText());
      // El 26/10 empieza a las 00:00 de Argentina, que son las 03:00 UTC.
      assertEquals("2026-10-26T03:00:00.000Z", datos.get("fecha").asText());

      MensajesCapturados.Enviado delDonador = rabbitMQ.todos().get(1);
      verificarSobre(delDonador, TipoEvento.DONADOR_MODIFICADO, donadorID);
      assertEquals(1, delDonador.datos().get("cantidadQuejas").asInt());
      assertEquals("VERIFICADO", delDonador.datos().get("estado").asText());
    }

    @Test
    @DisplayName("La quinta queja publica QUEJA_REGISTRADA y DONADOR_MODIFICADO ya en SOSPECHOSO")
    void quintaQuejaLoVuelveSospechoso() {
      String donadorID = crearDonador();
      for (int i = 1; i <= 4; i++) {
        fachada.agregarQueja(queja(donadorID, "donacion" + i));
      }
      rabbitMQ.limpiar();

      fachada.agregarQueja(queja(donadorID, "donacion5"));

      assertEquals(List.of("QUEJA_REGISTRADA", "DONADOR_MODIFICADO"), rabbitMQ.tipos());
      JsonNode donador = rabbitMQ.ultimo().datos();
      assertEquals("SOSPECHOSO", donador.get("estado").asText());
      assertEquals(5, donador.get("cantidadQuejas").asInt());
    }

    @Test
    @DisplayName("Una queja rechazada no publica nada")
    void quejaRechazada() {
      String donadorID = crearDonador();
      rabbitMQ.limpiar();

      assertThrows(
          RuntimeException.class,
          () -> fachada.agregarQueja(new QuejaDTO(null, "donacion1", donadorID, null, " ")));

      assertTrue(rabbitMQ.todos().isEmpty(), rabbitMQ.tipos().toString());
    }

    @Test
    @DisplayName("Una queja sin fecha sale con el comienzo del día de hoy en Argentina, en UTC")
    void quejaSinFecha() {
      String donadorID = crearDonador();
      rabbitMQ.limpiar();

      fachada.agregarQueja(new QuejaDTO(null, "donacion1", donadorID, null, "llegó vencido"));

      assertEquals(
          FechasUtc.inicioDelDia(LocalDate.now()),
          rabbitMQ.todos().get(0).datos().get("fecha").asText());
    }
  }

  @Nested
  @DisplayName("Entidades")
  class Entidades {

    @Test
    @DisplayName("El alta publica ENTIDAD_CREADA solo con id y razón social")
    void alta() {
      String id = crearEntidad();

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.ENTIDAD_CREADA, id);
      assertEquals(Set.of("id", "razonSocial"), MensajesCapturados.campos(enviado.datos()));
      assertEquals("Comedor Los Pibes", enviado.datos().get("razonSocial").asText());
      for (String prohibido : List.of("Av. Siempreviva 742", "11-4444-5555", "comedor@mail.com")) {
        assertFalse(enviado.cuerpo().contains(prohibido), "lleva " + prohibido);
      }
    }

    @Test
    @DisplayName("Editar una entidad publica ENTIDAD_MODIFICADA con la razón social nueva")
    void edicion() {
      String id = crearEntidad();
      rabbitMQ.limpiar();

      fachada.editarEntidad(id, new EntidadBeneficaDTO(null, "Comedor Nuevo", null, null, null));

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.ENTIDAD_MODIFICADA, id);
      assertEquals("Comedor Nuevo", enviado.datos().get("razonSocial").asText());
    }
  }

  @Nested
  @DisplayName("Necesidades")
  class Necesidades {

    @Test
    @DisplayName("El alta publica NECESIDAD_REGISTRADA con todos los campos del contrato")
    void alta() {
      String entidadID = crearEntidad();
      rabbitMQ.limpiar();

      String id =
          fachada
              .registrarNecesidad(
                  new NecesidadMaterialDTO(
                      null, entidadID, 7, "frazadas", 20, "p1",
                      TipoNecesidadMaterialEnum.RECURRENTE))
              .id();

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.NECESIDAD_REGISTRADA, id);
      JsonNode datos = enviado.datos();
      assertEquals(
          Set.of(
              "id", "entidadID", "productoSolicitadoID", "nivelDeUrgencia", "tipo",
              "cantidadObjetivo", "cantidadCubierta", "descripcion"),
          MensajesCapturados.campos(datos));
      assertEquals(id, datos.get("id").asText());
      assertEquals(entidadID, datos.get("entidadID").asText());
      assertEquals("p1", datos.get("productoSolicitadoID").asText());
      assertEquals(7, datos.get("nivelDeUrgencia").asInt());
      assertEquals("RECURRENTE", datos.get("tipo").asText());
      assertEquals(20, datos.get("cantidadObjetivo").asInt());
      assertEquals(0, datos.get("cantidadCubierta").asInt());
      assertEquals("frazadas", datos.get("descripcion").asText());
    }

    @Test
    @DisplayName("Con stock al crear, sale un solo NECESIDAD_REGISTRADA con la cubierta final")
    void altaConStock() {
      String entidadID = crearEntidad();
      LogisticaStockClient logistica = mock(LogisticaStockClient.class);
      when(logistica.consultarStockDisponible("p1")).thenReturn(OptionalInt.of(6));
      when(logistica.solicitarAsignacion(anyString(), anyString(), anyInt())).thenReturn(true);
      fachada.setLogisticaStockClient(logistica);
      rabbitMQ.limpiar();

      fachada.registrarNecesidad(
          new NecesidadMaterialDTO(
              null, entidadID, 7, "frazadas", 10, "p1", TipoNecesidadMaterialEnum.EXTRAORDINARIA));

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      assertEquals("NECESIDAD_REGISTRADA", enviado.tipo());
      assertEquals(6, enviado.datos().get("cantidadCubierta").asInt());
    }

    @Test
    @DisplayName("Si Logística no confirma la asignación, NECESIDAD_REGISTRADA sale con 0 cubierto")
    void altaSinConfirmacion() {
      String entidadID = crearEntidad();
      LogisticaStockClient logistica = mock(LogisticaStockClient.class);
      when(logistica.consultarStockDisponible("p1")).thenReturn(OptionalInt.of(6));
      when(logistica.solicitarAsignacion(anyString(), anyString(), anyInt())).thenReturn(false);
      fachada.setLogisticaStockClient(logistica);
      rabbitMQ.limpiar();

      fachada.registrarNecesidad(
          new NecesidadMaterialDTO(
              null, entidadID, 7, "frazadas", 10, "p1", TipoNecesidadMaterialEnum.EXTRAORDINARIA));

      assertEquals(0, rabbitMQ.unico().datos().get("cantidadCubierta").asInt());
    }

    @Test
    @DisplayName("Si el paso de stock falla de forma inesperada, el alta guardada se publica igual")
    void altaConFallaInesperadaDeLogistica() {
      String entidadID = crearEntidad();
      LogisticaStockClient logistica = mock(LogisticaStockClient.class);
      when(logistica.consultarStockDisponible("p1")).thenThrow(new IllegalStateException("boom"));
      fachada.setLogisticaStockClient(logistica);
      rabbitMQ.limpiar();

      assertThrows(
          IllegalStateException.class,
          () ->
              fachada.registrarNecesidad(
                  new NecesidadMaterialDTO(
                      null, entidadID, 7, "frazadas", 10, "p1",
                      TipoNecesidadMaterialEnum.EXTRAORDINARIA)));

      // La necesidad quedó guardada antes de llamar a Logística: Consultas se tiene que enterar.
      assertEquals(List.of("NECESIDAD_REGISTRADA"), rabbitMQ.tipos());
    }

    @Test
    @DisplayName("Satisfacer publica NECESIDAD_MODIFICADA con la cantidad cubierta nueva")
    void satisfaccion() {
      String id = crearNecesidad(10, "p1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);
      fachada.satisfacerNecesidad(id, 3);
      rabbitMQ.limpiar();

      fachada.satisfacerNecesidad(id, 4);

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.NECESIDAD_MODIFICADA, id);
      assertEquals(7, enviado.datos().get("cantidadCubierta").asInt());
      assertEquals(10, enviado.datos().get("cantidadObjetivo").asInt());
    }

    @Test
    @DisplayName("Una satisfacción rechazada no publica nada")
    void satisfaccionRechazada() {
      String id = crearNecesidad(10, "p1", TipoNecesidadMaterialEnum.RECURRENTE);
      rabbitMQ.limpiar();

      assertThrows(RuntimeException.class, () -> fachada.satisfacerNecesidad(id, 4));

      assertTrue(rabbitMQ.todos().isEmpty(), rabbitMQ.tipos().toString());
    }

    @Test
    @DisplayName("Editar publica NECESIDAD_MODIFICADA con los datos nuevos")
    void edicion() {
      String id = crearNecesidad(10, "p1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);
      rabbitMQ.limpiar();

      fachada.modificarNecesidad(
          id, new NecesidadMaterialDTO(null, null, 9, "frazadas y mantas", 12, null, null));

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.NECESIDAD_MODIFICADA, id);
      assertEquals("frazadas y mantas", enviado.datos().get("descripcion").asText());
      assertEquals(9, enviado.datos().get("nivelDeUrgencia").asInt());
      assertEquals(12, enviado.datos().get("cantidadObjetivo").asInt());
    }

    @Test
    @DisplayName("Borrar publica NECESIDAD_ELIMINADA solo con el id")
    void baja() {
      String id = crearNecesidad(10, "p1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);
      rabbitMQ.limpiar();

      fachada.eliminarNecesidad(id);

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.NECESIDAD_ELIMINADA, id);
      assertEquals(Set.of("id"), MensajesCapturados.campos(enviado.datos()));
      assertEquals(id, enviado.datos().get("id").asText());
    }

    @Test
    @DisplayName("Borrar una necesidad que no existe no publica nada")
    void bajaInexistente() {
      assertThrows(NoSuchElementException.class, () -> fachada.eliminarNecesidad("999"));

      assertTrue(rabbitMQ.todos().isEmpty(), rabbitMQ.tipos().toString());
    }
  }

  @Nested
  @DisplayName("Entregas simultáneas de una necesidad")
  class EntregasSimultaneas {

    @Test
    @DisplayName("Diez entregas a la vez: la de hora más nueva es la que dejó la necesidad completa")
    void ordenDeLosEventos() throws InterruptedException {
      String id = crearNecesidad(20, "p1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);
      rabbitMQ.limpiar();

      int hilos = 10;
      CountDownLatch salida = new CountDownLatch(1);
      CountDownLatch terminaron = new CountDownLatch(hilos);
      for (int i = 0; i < hilos; i++) {
        new Thread(
                () -> {
                  try {
                    salida.await();
                    fachada.satisfacerNecesidad(id, 2);
                  } catch (Exception e) {
                    // si falla, faltará un evento y el assert lo delata
                  } finally {
                    terminaron.countDown();
                  }
                })
            .start();
      }
      salida.countDown();
      assertTrue(terminaron.await(10, TimeUnit.SECONDS));

      // Consultas se queda con el evento de hora más nueva. Ordenados por hora, las cubiertas
      // tienen que crecer de a 2 sin empates: si no, el modelo de lectura podría quedar atrasado.
      List<MensajesCapturados.Enviado> porHora = new ArrayList<>(rabbitMQ.todos());
      porHora.sort(Comparator.comparing(MensajesCapturados.Enviado::ocurridoEn));
      assertEquals(
          List.of(2, 4, 6, 8, 10, 12, 14, 16, 18, 20),
          porHora.stream().map(e -> e.datos().get("cantidadCubierta").asInt()).toList());
      assertEquals(
          hilos, porHora.stream().map(MensajesCapturados.Enviado::ocurridoEn).distinct().count());
    }

    @Test
    @DisplayName("Con RabbitMQ trabado en una publicación, otra entrega de la misma necesidad no espera")
    void publicarNoAlargaElCandado() throws InterruptedException {
      String id = crearNecesidad(20, "p1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);
      RabbitOperations lento = mock(RabbitOperations.class);
      CountDownLatch primerEnvioEnCurso = new CountDownLatch(1);
      CountDownLatch liberar = new CountDownLatch(1);
      AtomicInteger envios = new AtomicInteger();
      doAnswer(
              invocacion -> {
                if (envios.getAndIncrement() == 0) {
                  primerEnvioEnCurso.countDown();
                  liberar.await(10, TimeUnit.SECONDS);
                }
                return null;
              })
          .when(lento)
          .send(anyString(), anyString(), any(Message.class));
      conectarPublicador(lento);

      Thread primera = new Thread(() -> fachada.satisfacerNecesidad(id, 2));
      primera.start();
      assertTrue(primerEnvioEnCurso.await(5, TimeUnit.SECONDS));

      // Si se publicara dentro del bloque sincronizado, esta entrega quedaría esperando a RabbitMQ.
      assertTimeoutPreemptively(Duration.ofSeconds(3), () -> fachada.satisfacerNecesidad(id, 3));

      liberar.countDown();
      primera.join(5000);
      assertEquals(5, fachada.buscarNecesidadPorID(id).cantidadActual());
    }
  }

  @Nested
  @DisplayName("Módulo y mensaje")
  class ModuloYMensaje {

    @Test
    @DisplayName("Resetear la base publica MODULO_RESETEADO con entidadId * y datos vacíos")
    void reset() {
      crearDonador();
      rabbitMQ.limpiar();

      fachada.resetBaseDeDatos();

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      verificarSobre(enviado, TipoEvento.MODULO_RESETEADO, "*");
      assertTrue(enviado.datos().isObject());
      assertEquals(0, enviado.datos().size());
    }

    @Test
    @DisplayName("El mensaje lleva el traceId del pedido, el id del evento y el tipo")
    void propiedadesDelMensaje() {
      MDC.put("traceId", "abc123def456");

      crearDonador();

      MensajesCapturados.Enviado enviado = rabbitMQ.unico();
      var propiedades = enviado.mensaje().getMessageProperties();
      assertEquals("abc123def456", propiedades.getHeader(TrazabilidadFilter.HEADER_TRACE));
      assertEquals(enviado.sobre().get("eventoId").asText(), propiedades.getMessageId());
      assertEquals("DONADOR_CREADO", propiedades.getType());
      assertEquals("UTF-8", propiedades.getContentEncoding());
    }

    @Test
    @DisplayName("Sin traceId en el MDC el mensaje sale igual, sin ese header")
    void sinTraceId() {
      crearDonador();

      assertNull(
          rabbitMQ.unico().mensaje().getMessageProperties().getHeader(
              TrazabilidadFilter.HEADER_TRACE));
    }

    @Test
    @DisplayName("Si falla el armado de un evento, la operación responde igual y se cuenta")
    void fallaAlArmar() {
      EventosDonadores roto = mock(EventosDonadores.class);
      when(roto.donadorCreado(any())).thenThrow(new IllegalStateException("datos raros"));
      fachada.setEventos(roto);

      String id = assertDoesNotThrow(() -> crearDonador());

      assertEquals("Ana", fachada.buscarDonadorPorID(id).nombre());
      assertEquals(1, registro.find("eventos.fallidos").counter().count());
    }
  }

  @Nested
  @DisplayName("Republicación")
  class Republicacion {

    @Test
    @DisplayName("El estado actual sale como modificaciones y quejas, en ese orden")
    void estadoActual() {
      String donadorID = crearDonador();
      fachada.agregarQueja(queja(donadorID, "donacion1"));
      String necesidadID = crearNecesidad(10, "p1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);
      fachada.satisfacerNecesidad(necesidadID, 4);

      List<EventoDonadores> eventos = fachada.eventosDelEstadoActual();

      assertEquals(
          List.of(
              "DONADOR_MODIFICADO", "ENTIDAD_MODIFICADA", "NECESIDAD_MODIFICADA",
              "QUEJA_REGISTRADA"),
          eventos.stream().map(e -> e.sobre().tipo()).toList());
      DatosEvento.DeNecesidad necesidad = (DatosEvento.DeNecesidad) eventos.get(2).sobre().datos();
      assertEquals(4, necesidad.cantidadCubierta());
      DatosEvento.DeDonador donador = (DatosEvento.DeDonador) eventos.get(0).sobre().datos();
      assertEquals(1, donador.cantidadQuejas());
    }

    @Test
    @DisplayName("Sin catálogo de eventos (constructor de cátedra) no hay nada que republicar")
    void sinEventos() {
      Fachada deCatedra = new Fachada();
      deCatedra.agregarDonador(
          new DonadorDTO(null, "Ana", "Pérez", 30, "a@mail.com", "1", "Calle 1", null, null));

      assertTrue(deCatedra.eventosDelEstadoActual().isEmpty());
    }
  }
}
