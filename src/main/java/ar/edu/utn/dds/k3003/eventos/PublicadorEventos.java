package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.MetricasService;
import ar.edu.utn.dds.k3003.logging.TrazabilidadFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda los eventos del módulo al exchange {@code donatrack.eventos} de RabbitMQ.
 *
 * <p>Tres reglas del contrato viven acá:
 *
 * <ul>
 *   <li><b>Después del commit.</b> Escucha los eventos de aplicación en {@code AFTER_COMMIT}: un
 *       cambio que se deshizo no se publica. Con {@code fallbackExecution} también salen los de las
 *       operaciones sin transacción propia (casi todas: cada {@code save} confirma la suya), que en
 *       ese caso ya están guardadas cuando se emiten.
 *   <li><b>Publicar nunca hace fallar la operación.</b> Ninguna excepción sale de acá: si RabbitMQ
 *       falla, WARN en el log, se cuenta en {@code eventos.fallidos} y la operación sigue. Con
 *       {@code fallbackExecution} el listener corre en el hilo del pedido, así que una excepción
 *       que escapara le llegaría al cliente como un error de una operación que sí se hizo.
 *   <li><b>Solo publica.</b> No declara colas ni el exchange: lo creó Logística (topic, durable), y
 *       redeclararlo con otros argumentos haría que el broker cierre el canal.
 * </ul>
 *
 * <p>Sin {@code RABBITMQ_URL} (local, tests) queda deshabilitado: lo avisa una vez al arrancar y no
 * intenta conectar. La conexión de Spring es perezosa, así que nada la abre si acá no se usa.
 */
@Component
public class PublicadorEventos {

  public static final String EXCHANGE = "donatrack.eventos";

  private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

  private final RabbitOperations rabbit;
  private final boolean habilitado;
  private final MetricasService metricas;

  // Propio y no el de Spring: el cuerpo del mensaje es contrato con otro servicio, y no puede
  // cambiar porque alguien toque spring.jackson.* para la API REST. Las fechas ya van como texto.
  private final ObjectMapper json = new ObjectMapper();

  @Autowired
  public PublicadorEventos(
      ObjectProvider<RabbitOperations> rabbit,
      @Value("${spring.rabbitmq.addresses:}") String direcciones,
      MetricasService metricas) {
    this(rabbit.getIfAvailable(), direcciones, metricas);
  }

  public PublicadorEventos(RabbitOperations rabbit, String direcciones, MetricasService metricas) {
    this.rabbit = rabbit;
    this.metricas = metricas;
    this.habilitado = rabbit != null && direcciones != null && !direcciones.isBlank();
    // La dirección no se loguea: trae usuario y contraseña.
    if (habilitado) {
      log.info("[Donadores -> RabbitMQ] Publicador de eventos habilitado (exchange {})", EXCHANGE);
    } else if (rabbit == null) {
      log.warn("[Donadores -> RabbitMQ] Publicador de eventos deshabilitado: no hay RabbitTemplate");
    } else {
      log.info(
          "[Donadores -> RabbitMQ] Publicador de eventos deshabilitado: no está definida"
              + " RABBITMQ_URL. Los eventos no se publican y no se intenta conectar.");
    }
  }

  public boolean estaHabilitado() {
    return habilitado;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void alConfirmarse(EventoDonadores evento) {
    publicar(evento);
  }

  /**
   * Publica un evento. Nunca lanza.
   *
   * @return si RabbitMQ lo aceptó; {@code false} si falló o si el publicador está deshabilitado
   */
  public boolean publicar(EventoDonadores evento) {
    if (!habilitado || evento == null) {
      return false;
    }
    SobreEvento sobre = evento.sobre();
    try {
      rabbit.send(EXCHANGE, evento.routingKey(), mensaje(sobre));
      metricas.registrarEventoPublicado(sobre.tipo());
      return true;
    } catch (Exception e) {
      metricas.registrarEventoFallido(sobre.tipo());
      log.warn(
          "[Donadores -> RabbitMQ] No se pudo publicar {} de {} (eventoId={}): {}",
          sobre.tipo(),
          sobre.entidadId(),
          sobre.eventoId(),
          e.getMessage());
      return false;
    }
  }

  /** Para la republicación: publica todos en orden y cuenta cómo salió. */
  public ResultadoRepublicacion publicarTodos(List<EventoDonadores> eventos) {
    int publicados = 0;
    int fallidos = 0;
    for (EventoDonadores evento : eventos) {
      if (publicar(evento)) {
        publicados++;
      } else {
        fallidos++;
      }
    }
    return new ResultadoRepublicacion(publicados, fallidos);
  }

  private Message mensaje(SobreEvento sobre) throws Exception {
    MessageProperties propiedades = new MessageProperties();
    propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    propiedades.setContentEncoding(StandardCharsets.UTF_8.name());
    // Persistente: si el broker se reinicia antes de que Consultas lo lea, el mensaje no se pierde.
    propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    propiedades.setMessageId(sobre.eventoId());
    propiedades.setType(sobre.tipo());
    propiedades.setAppId(EventosDonadores.MODULO);
    propiedades.setTimestamp(new Date());
    // Mismo traceId que el pedido que causó el cambio: en Datadog, el procesamiento en Consultas
    // queda en el mismo recorrido que la operación original.
    String traceId = MDC.get("traceId");
    if (traceId != null && !traceId.isBlank()) {
      propiedades.setHeader(TrazabilidadFilter.HEADER_TRACE, traceId);
    }
    return new Message(json.writeValueAsBytes(sobre), propiedades);
  }
}
