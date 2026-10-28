package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.MetricasService;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.repositories.InMemoryDonadoresRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryEntidadesRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryNecesidadesRepo;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;

/**
 * Publicar nunca hace fallar la operación, y sin {@code RABBITMQ_URL} no se intenta nada.
 */
class PublicadorEventosTest {

  private SimpleMeterRegistry registro;
  private MetricasService metricas;
  private RabbitOperations rabbit;
  private EventosDonadores catalogo;

  private Logger logger;
  private ListAppender<ILoggingEvent> logs;

  @BeforeEach
  void setUp() {
    registro = new SimpleMeterRegistry();
    metricas = new MetricasService(registro);
    rabbit = mock(RabbitOperations.class);
    catalogo = new EventosDonadores(evento -> {});

    logger = (Logger) LoggerFactory.getLogger(PublicadorEventos.class);
    logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logs);
  }

  private EventoDonadores unEvento() {
    return catalogo.necesidadEliminada("7");
  }

  private double contador(String nombre, String tipo) {
    return registro.find(nombre).tag("tipo", tipo).counters().stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private List<String> mensajes(Level nivel) {
    return logs.list.stream()
        .filter(e -> e.getLevel() == nivel)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private void fallaRabbit() {
    doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class));
  }

  @Test
  @DisplayName("Sin RABBITMQ_URL queda deshabilitado: lo avisa una sola vez y no toca RabbitMQ")
  void sinDireccion() {
    for (String direccion : new String[] {"", "  ", null}) {
      logs.list.clear();
      PublicadorEventos publicador = new PublicadorEventos(rabbit, direccion, metricas);

      assertFalse(publicador.estaHabilitado());
      assertFalse(publicador.publicar(unEvento()));
      publicador.alConfirmarse(unEvento());
      publicador.alConfirmarse(unEvento());

      List<String> avisos = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertEquals(1, avisos.size(), avisos.toString());
      assertTrue(avisos.get(0).contains("RABBITMQ_URL"), avisos.get(0));
    }
    verifyNoInteractions(rabbit);
    // Apagado no es fallar: no se cuenta como evento perdido.
    assertTrue(registro.find("eventos.fallidos").counters().isEmpty());
  }

  @Test
  @DisplayName("Sin RabbitTemplate queda deshabilitado aunque haya dirección")
  void sinRabbitTemplate() {
    PublicadorEventos publicador =
        new PublicadorEventos((RabbitOperations) null, "amqp://localhost", metricas);

    assertFalse(publicador.estaHabilitado());
    assertFalse(publicador.publicar(unEvento()));
  }

  @Test
  @DisplayName("Si RabbitMQ acepta el mensaje, se cuenta como publicado")
  void publicado() {
    doNothing().when(rabbit).send(anyString(), anyString(), any(Message.class));
    PublicadorEventos publicador = new PublicadorEventos(rabbit, "amqp://localhost", metricas);

    assertTrue(publicador.publicar(unEvento()));

    assertEquals(1, contador("eventos.publicados", "NECESIDAD_ELIMINADA"));
    org.mockito.Mockito.verify(rabbit)
        .send(eq("donatrack.eventos"), eq("donadores.necesidad.eliminada"), any(Message.class));
  }

  @Test
  @DisplayName("Si RabbitMQ falla, no lanza: avisa en WARN y lo cuenta como fallido")
  void rabbitFalla() {
    fallaRabbit();
    PublicadorEventos publicador = new PublicadorEventos(rabbit, "amqp://localhost", metricas);

    assertFalse(publicador.publicar(unEvento()));
    publicador.alConfirmarse(unEvento());

    assertEquals(2, contador("eventos.fallidos", "NECESIDAD_ELIMINADA"));
    assertEquals(0, contador("eventos.publicados", "NECESIDAD_ELIMINADA"));
    List<String> advertencias = mensajes(Level.WARN);
    assertEquals(2, advertencias.size(), advertencias.toString());
    assertTrue(advertencias.get(0).contains("NECESIDAD_ELIMINADA"), advertencias.get(0));
    assertTrue(advertencias.get(0).contains("Connection refused"), advertencias.get(0));
  }

  @Test
  @DisplayName("Con RabbitMQ caído, el alta de un donador y una queja responden bien y se cuentan")
  void laOperacionSigue() {
    fallaRabbit();
    PublicadorEventos publicador = new PublicadorEventos(rabbit, "amqp://localhost", metricas);
    Fachada fachada =
        new Fachada(
            new InMemoryDonadoresRepo(), new InMemoryEntidadesRepo(), new InMemoryNecesidadesRepo(),
            metricas);
    fachada.setEventos(new EventosDonadores(e -> publicador.alConfirmarse((EventoDonadores) e)));

    DonadorDTO donador =
        fachada.agregarDonador(
            new DonadorDTO(null, "Ana", "Pérez", 30, "a@mail.com", "301", "Calle 1", null, null));
    QuejaDTO queja =
        fachada.agregarQueja(new QuejaDTO(null, "donacion1", donador.id(), null, "llegó roto"));

    assertEquals("Ana", fachada.buscarDonadorPorID(donador.id()).nombre());
    assertEquals(queja.id(), fachada.obtenerQuejasDe(donador.id()).get(0).id());
    assertEquals(1, contador("eventos.fallidos", "DONADOR_CREADO"));
    assertEquals(1, contador("eventos.fallidos", "QUEJA_REGISTRADA"));
    assertEquals(1, contador("eventos.fallidos", "DONADOR_MODIFICADO"));
  }

  @Test
  @DisplayName("Republicar cuenta por separado los publicados y los fallidos")
  void publicarTodos() {
    PublicadorEventos publicador = new PublicadorEventos(rabbit, "amqp://localhost", metricas);
    doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
        .when(rabbit)
        .send(anyString(), eq("donadores.modulo.reseteado"), any(Message.class));

    ResultadoRepublicacion resultado =
        publicador.publicarTodos(
            List.of(unEvento(), catalogo.moduloReseteado(), catalogo.necesidadEliminada("8")));

    assertEquals(new ResultadoRepublicacion(2, 1), resultado);
  }
}
