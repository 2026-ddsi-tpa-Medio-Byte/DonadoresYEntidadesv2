package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorStatsDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.InsigniaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.TipoMisionEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import ar.edu.utn.dds.k3003.controllers.responses.NecesidadResponse;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.DonadorYaExistenteException;
import ar.edu.utn.dds.k3003.model.CambioEstadoDonador;
import ar.edu.utn.dds.k3003.repositories.InMemoryDonadoresRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryEntidadesRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryNecesidadesRepo;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Validaciones y registros que le faltaban a la fachada.
 *
 * <p>Corre sobre repositorios en memoria y un registro de métricas propio, para poder mirar qué
 * quedó guardado y qué llamadas salientes se contaron. Los otros módulos son mocks.
 */
@ExtendWith(MockitoExtension.class)
class ValidacionesFachadaTest {

  private InMemoryDonadoresRepo donadores;
  private SimpleMeterRegistry registro;
  private Fachada fachada;

  @Mock private FachadaDonaciones fachadaDonaciones;
  @Mock private FachadaIncentivos fachadaIncentivos;

  @BeforeEach
  void setUp() {
    donadores = new InMemoryDonadoresRepo();
    registro = new SimpleMeterRegistry();
    fachada =
        new Fachada(
            donadores,
            new InMemoryEntidadesRepo(),
            new InMemoryNecesidadesRepo(),
            new MetricasService(registro));
  }

  private DonadorDTO donador(String nroDocumento) {
    return new DonadorDTO(
        null, "Ana", "Pérez", 30, "ana@mail.com", nroDocumento, "Calle 1", null, null);
  }

  private String crearDonador() {
    return fachada.agregarDonador(donador("30111222")).id();
  }

  /** Se crea antes de enchufar Donaciones: la validación del producto se prueba aparte. */
  private String crearNecesidad(int objetivo, String productoID) {
    String entidadID =
        fachada
            .agregarEntidad(new EntidadBeneficaDTO(null, "Comedor", "Calle 1", "11", "c@mail.com"))
            .id();
    return fachada
        .registrarNecesidad(
            new NecesidadMaterialDTO(
                null, entidadID, 5, "necesidad", objetivo, productoID,
                TipoNecesidadMaterialEnum.EXTRAORDINARIA))
        .id();
  }

  /** Cuántas llamadas salientes se contaron con esas etiquetas. */
  private double llamadas(String operacion, String destino, String resultado) {
    return registro.find("integracion.llamadas")
        .tag("operacion", operacion)
        .tag("destino", destino)
        .tag("resultado", resultado)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  @Nested
  @DisplayName("Cambio de estado a mano")
  class CambioDeEstado {

    @Test
    @DisplayName("Cambiar el estado deja registrado el cambio, con fecha y motivo")
    void dejaRegistroDelCambio() {
      String id = crearDonador();

      fachada.modificarEstado(id, EstadoDonadorEnum.BANEADO);

      List<CambioEstadoDonador> cambios = donadores.findById(id).orElseThrow().getCambiosEstado();
      assertEquals(2, cambios.size(), "tienen que quedar el alta y el cambio manual");
      CambioEstadoDonador ultimo = cambios.get(cambios.size() - 1);
      assertEquals(EstadoDonadorEnum.BANEADO, ultimo.getEstado());
      assertEquals(LocalDate.now(), ultimo.getFecha());
      assertEquals("Cambio manual de estado", ultimo.getMotivo());
    }
  }

  @Nested
  @DisplayName("Registro de quejas")
  class Quejas {

    @Test
    @DisplayName("Una queja sin descripción se rechaza y no se guarda")
    void sinDescripcion() {
      String id = crearDonador();

      for (String descripcion : Arrays.asList(null, "", "   ")) {
        RuntimeException e =
            assertThrows(
                RuntimeException.class,
                () -> fachada.agregarQueja(new QuejaDTO(null, "donacion1", id, null, descripcion)));
        assertEquals("La queja tiene que tener una descripción", e.getMessage());
      }
      assertTrue(fachada.obtenerQuejasDe(id).isEmpty());
    }

    @Test
    @DisplayName("Una queja sin donador es un pedido inválido, no un donador inexistente")
    void sinDonador() {
      for (String donadorID : Arrays.asList(null, "", "  ")) {
        RuntimeException e =
            assertThrows(
                RuntimeException.class,
                () ->
                    fachada.agregarQueja(
                        new QuejaDTO(null, "donacion1", donadorID, null, "llegó roto")));
        // Un 404 diría que el donador no existe, cuando en realidad el pedido no dijo cuál es.
        assertFalse(
            e instanceof NoSuchElementException || e instanceof DonadorNoEncontradoException,
            "terminaría en 404: " + e);
        assertEquals("La queja tiene que indicar el donador (donadorID)", e.getMessage());
      }
    }

    @Test
    @DisplayName("Una queja sin fecha queda registrada con la fecha de hoy")
    void sinFechaUsaHoy() {
      String id = crearDonador();

      QuejaDTO queja =
          fachada.agregarQueja(new QuejaDTO(null, "donacion1", id, null, "llegó vencido"));

      assertEquals(LocalDate.now(), queja.fecha());
      assertEquals(LocalDate.now(), fachada.obtenerQuejasDe(id).get(0).fecha());
    }

    @Test
    @DisplayName("Una queja con fecha conserva la que vino")
    void conFechaLaConserva() {
      String id = crearDonador();
      LocalDate ayer = LocalDate.now().minusDays(1);

      QuejaDTO queja =
          fachada.agregarQueja(new QuejaDTO(null, "donacion1", id, ayer, "llegó vencido"));

      assertEquals(ayer, queja.fecha());
    }
  }

  @Nested
  @DisplayName("Modificación de necesidades")
  class ModificarNecesidad {

    private NecesidadMaterialDTO cambio(Integer cantidadObjetivo, String productoID) {
      return new NecesidadMaterialDTO(null, null, null, null, cantidadObjetivo, productoID, null);
    }

    @Test
    @DisplayName("La cantidad objetivo nueva tiene que ser mayor a 0")
    void objetivoNoPositivo() {
      String id = crearNecesidad(10, null);

      for (int cantidad : new int[] {0, -4}) {
        RuntimeException e =
            assertThrows(
                RuntimeException.class, () -> fachada.modificarNecesidad(id, cambio(cantidad, null)));
        assertEquals("La cantidad objetivo de la necesidad debe ser mayor a 0", e.getMessage());
      }
      assertEquals(10, fachada.buscarNecesidadPorID(id).cantidadObjetivo());
    }

    @Test
    @DisplayName("No se puede bajar el objetivo por debajo de lo que ya se entregó")
    void objetivoMenorALoCubierto() {
      String id = crearNecesidad(10, null);
      fachada.satisfacerNecesidad(id, 6);

      RuntimeException e =
          assertThrows(
              RuntimeException.class, () -> fachada.modificarNecesidad(id, cambio(5, null)));

      assertEquals(
          "La cantidad objetivo (5) no puede ser menor a lo ya cubierto (6)", e.getMessage());
      assertEquals(10, fachada.buscarNecesidadPorID(id).cantidadObjetivo());
    }

    @Test
    @DisplayName("Bajar el objetivo justo a lo ya entregado se acepta y la deja satisfecha")
    void objetivoIgualALoCubierto() {
      String id = crearNecesidad(10, "p1");
      fachada.satisfacerNecesidad(id, 6);

      fachada.modificarNecesidad(id, cambio(6, null));

      assertTrue(fachada.obtenerNecesidadesInsatisfechasDe("p1").isEmpty());
    }

    @Test
    @DisplayName("Reenviar el mismo objetivo no se rechaza aunque lo cubierto ya lo haya superado")
    void mismoObjetivoConNecesidadSobrecubierta() {
      // Una extraordinaria acepta entregas por encima de lo que falta, así que lo cubierto puede
      // pasar el objetivo. El bot reenvía la necesidad completa al editar cualquier campo: si se
      // validara el objetivo sin que cambie, editar la descripción daría 400.
      String id = crearNecesidad(10, null);
      fachada.satisfacerNecesidad(id, 6);
      fachada.satisfacerNecesidad(id, 8);

      fachada.modificarNecesidad(
          id, new NecesidadMaterialDTO(null, null, 5, "frazadas y mantas", 10, null, null));

      assertEquals("frazadas y mantas", fachada.buscarNecesidadPorID(id).descripcion());
    }

    @Test
    @DisplayName("Cambiar a un producto que Donaciones no conoce se rechaza y no toca la necesidad")
    void productoInexistente() {
      String id = crearNecesidad(10, "p1");
      fachada.setFachadaDonaciones(fachadaDonaciones);
      when(fachadaDonaciones.buscarProductoPorID("noexiste"))
          .thenThrow(new NoSuchElementException("no existe"));

      RuntimeException e =
          assertThrows(
              RuntimeException.class, () -> fachada.modificarNecesidad(id, cambio(3, "noexiste")));

      assertEquals("El producto solicitado no existe en Donaciones: noexiste", e.getMessage());
      // Nada a medias: tampoco se aplicó el cambio de cantidad que venía en el mismo pedido.
      assertEquals("p1", fachada.buscarNecesidadPorID(id).productoSolicitadoID());
      assertEquals(10, fachada.buscarNecesidadPorID(id).cantidadObjetivo());
      assertEquals(1, llamadas("modificar_necesidad", "donaciones", "error"));
    }

    @Test
    @DisplayName("Cambiar a un producto válido se acepta y deja contada la consulta a Donaciones")
    void productoValido() {
      String id = crearNecesidad(10, "p1");
      fachada.setFachadaDonaciones(fachadaDonaciones);
      when(fachadaDonaciones.buscarProductoPorID("p2"))
          .thenReturn(new ProductoDTO("p2", "Fideos", "Fideos secos largos", "alimentos", "1"));

      fachada.modificarNecesidad(id, cambio(null, "p2"));

      assertEquals("p2", fachada.buscarNecesidadPorID(id).productoSolicitadoID());
      assertEquals(1, llamadas("modificar_necesidad", "donaciones", "ok"));
    }

    @Test
    @DisplayName("Si el producto no cambia no se le pregunta a Donaciones")
    void mismoProductoNoConsulta() {
      String id = crearNecesidad(10, "p1");
      fachada.setFachadaDonaciones(fachadaDonaciones);

      fachada.modificarNecesidad(
          id, new NecesidadMaterialDTO(null, null, 9, "otra descripción", null, "p1", null));

      verifyNoInteractions(fachadaDonaciones);
      assertEquals("otra descripción", fachada.buscarNecesidadPorID(id).descripcion());
    }
  }

  @Nested
  @DisplayName("Consulta y baja de necesidades")
  class ConsultaYBajaDeNecesidades {

    @Test
    @DisplayName("Borrar una necesidad que no existe avisa que no existe (404), no un pedido inválido")
    void borrarInexistente() {
      assertThrows(NoSuchElementException.class, () -> fachada.eliminarNecesidad("999999"));
    }

    @Test
    @DisplayName("Una necesidad sin producto no rompe la búsqueda de insatisfechas por producto")
    void necesidadSinProducto() {
      crearNecesidad(10, null);
      String conProducto = crearNecesidad(10, "p1");

      List<NecesidadMaterialDTO> basicas = fachada.obtenerNecesidadesInsatisfechasDe("p1");
      List<NecesidadResponse> detalladas =
          fachada.obtenerNecesidadesInsatisfechasDetalladasDe("p1");

      assertEquals(List.of(conProducto), basicas.stream().map(NecesidadMaterialDTO::id).toList());
      assertEquals(List.of(conProducto), detalladas.stream().map(NecesidadResponse::id).toList());
    }
  }

  @Nested
  @DisplayName("Estadísticas del donador frente a Incentivos")
  class EstadisticasConIncentivos {

    private Logger logger;
    private ListAppender<ILoggingEvent> eventos;

    @BeforeEach
    void escucharLogs() {
      logger = (Logger) LoggerFactory.getLogger(Fachada.class);
      eventos = new ListAppender<>();
      eventos.start();
      logger.addAppender(eventos);
    }

    @AfterEach
    void dejarDeEscuchar() {
      logger.detachAppender(eventos);
    }

    @Test
    @DisplayName("Con Incentivos caído salen los datos propios y cada fallo queda registrado")
    void incentivosCaido() {
      String id = crearDonador();
      fachada.setFachadaIncentivos(fachadaIncentivos);
      when(fachadaIncentivos.getInsigniasDeDonador(id))
          .thenThrow(new RuntimeException("Connection refused"));
      when(fachadaIncentivos.getMisionEnCursoDeDonador(id))
          .thenThrow(new RuntimeException("Read timed out"));

      DonadorStatsDTO stats = fachada.estadisticasDonador(id);

      assertEquals("Ana", stats.nombre());
      assertEquals(EstadoDonadorEnum.VERIFICADO, stats.estado());
      assertTrue(stats.insigniasID().isEmpty());
      assertNull(stats.misionActualID());

      List<String> advertencias =
          eventos.list.stream()
              .filter(e -> e.getLevel() == Level.WARN)
              .map(ILoggingEvent::getFormattedMessage)
              .toList();
      assertTrue(
          advertencias.stream().anyMatch(m -> m.contains("Connection refused")),
          advertencias.toString());
      assertTrue(
          advertencias.stream().anyMatch(m -> m.contains("Read timed out")),
          advertencias.toString());
      assertEquals(2, llamadas("estadisticas_donador", "incentivos", "error"));
    }

    @Test
    @DisplayName("Si Incentivos responde, las dos consultas quedan contadas como exitosas")
    void incentivosResponde() {
      String id = crearDonador();
      fachada.setFachadaIncentivos(fachadaIncentivos);
      when(fachadaIncentivos.getInsigniasDeDonador(id))
          .thenReturn(List.of(new InsigniaDTO("insignia1", "Primera", "primera donación")));
      when(fachadaIncentivos.getMisionEnCursoDeDonador(id))
          .thenReturn(
              new MisionDTO(
                  "mision1", "Donar 5", "insignia1", null, null,
                  TipoMisionEnum.DONACIONES_EXITOSAS));

      DonadorStatsDTO stats = fachada.estadisticasDonador(id);

      assertEquals(List.of("insignia1"), stats.insigniasID());
      assertEquals("mision1", stats.misionActualID());
      assertEquals(2, llamadas("estadisticas_donador", "incentivos", "ok"));
      assertEquals(0, llamadas("estadisticas_donador", "incentivos", "error"));
    }
  }

  @Nested
  @DisplayName("Alta de donadores y entidades")
  class Altas {

    @Test
    @DisplayName("Dar de alta un donador sin datos explica qué pasó en vez de fallar con un NPE")
    void donadorNulo() {
      RuntimeException e = assertThrows(RuntimeException.class, () -> fachada.agregarDonador(null));

      assertFalse(e instanceof NullPointerException, "no puede ser un NPE: " + e);
      assertEquals("No llegaron los datos del donador", e.getMessage());
    }

    @Test
    @DisplayName("Los campos obligatorios vacíos o en blanco se rechazan nombrando cuáles faltan")
    void camposObligatorios() {
      DonadorDTO incompleto =
          new DonadorDTO(null, "  ", "Pérez", null, null, "30111222", "", null, null);

      RuntimeException e =
          assertThrows(RuntimeException.class, () -> fachada.agregarDonador(incompleto));

      assertEquals(
          "Faltan datos obligatorios del donador: nombre, edad, email, domicilio", e.getMessage());
      assertTrue(fachada.buscarTodosLosDonadores().isEmpty());
    }

    @Test
    @DisplayName("La edad tiene que ser mayor a 0")
    void edadNoPositiva() {
      for (int edad : new int[] {0, -3}) {
        DonadorDTO dto =
            new DonadorDTO(
                null, "Ana", "Pérez", edad, "ana@mail.com", "30111222", "Calle 1", null, null);

        RuntimeException e =
            assertThrows(RuntimeException.class, () -> fachada.agregarDonador(dto));
        assertEquals("La edad del donador debe ser mayor a 0", e.getMessage());
      }
      assertTrue(fachada.buscarTodosLosDonadores().isEmpty());
    }

    @Test
    @DisplayName("No se pueden dar de alta dos donadores con el mismo documento")
    void documentoRepetido() {
      fachada.agregarDonador(donador("30111222"));

      DonadorYaExistenteException e =
          assertThrows(
              DonadorYaExistenteException.class, () -> fachada.agregarDonador(donador("30111222")));

      assertEquals("Ya existe un donador con el documento 30111222", e.getMessage());
      assertEquals(1, fachada.buscarTodosLosDonadores().size());
    }

    @Test
    @DisplayName("El email y la categoría no se validan: cátedra e Incentivos usan valores libres")
    void emailYCategoriaLibres() {
      // Mismo donador que el fixture de cátedra, que usa "donador1" en todos los campos.
      DonadorDTO comoElDeCatedra =
          new DonadorDTO(
              null, "donador1", "donador1", 5, "donador1", "donador1", "donador1",
              EstadoDonadorEnum.VERIFICADO, "donador1");

      assertDoesNotThrow(() -> fachada.agregarDonador(comoElDeCatedra));
    }

    @Test
    @DisplayName("Una entidad sin razón social se rechaza")
    void entidadSinRazonSocial() {
      for (String razonSocial : Arrays.asList(null, "", "   ")) {
        RuntimeException e =
            assertThrows(
                RuntimeException.class,
                () ->
                    fachada.agregarEntidad(
                        new EntidadBeneficaDTO(null, razonSocial, "Calle 1", "11", "c@mail.com")));
        assertEquals("La razón social de la entidad es obligatoria", e.getMessage());
      }
      assertTrue(fachada.buscarTodasLasEntidades().isEmpty());
    }
  }
}
