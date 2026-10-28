package ar.edu.utn.dds.k3003.eventos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;

/**
 * Lo que el módulo le mandó a un RabbitMQ simulado, ya leído como JSON.
 *
 * <p>Se mira el mensaje tal como sale, bytes incluidos, y no el objeto antes de serializar: lo que
 * importa del contrato (nombres de campo, fechas en {@code Z}, que no viajen datos personales) se
 * decide recién al pasarlo a JSON.
 */
final class MensajesCapturados {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Un mensaje enviado. */
  record Enviado(String exchange, String routingKey, Message mensaje, JsonNode sobre) {

    String tipo() {
      return sobre.get("tipo").asText();
    }

    String entidadId() {
      return sobre.get("entidadId").asText();
    }

    String ocurridoEn() {
      return sobre.get("ocurridoEn").asText();
    }

    JsonNode datos() {
      return sobre.get("datos");
    }

    String cuerpo() {
      return new String(mensaje.getBody(), StandardCharsets.UTF_8);
    }
  }

  private final List<Enviado> enviados = new CopyOnWriteArrayList<>();

  private MensajesCapturados() {}

  /** Empieza a juntar lo que se le mande a ese RabbitTemplate simulado. */
  static MensajesCapturados en(RabbitOperations rabbit) {
    MensajesCapturados capturados = new MensajesCapturados();
    // lenient: varios tests verifican justamente que no se mande nada.
    lenient()
        .doAnswer(
            invocacion -> {
              capturados.agregar(
                  invocacion.getArgument(0), invocacion.getArgument(1), invocacion.getArgument(2));
              return null;
            })
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class));
    return capturados;
  }

  void agregar(String exchange, String routingKey, Message mensaje) {
    try {
      enviados.add(new Enviado(exchange, routingKey, mensaje, JSON.readTree(mensaje.getBody())));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  List<Enviado> todos() {
    return new ArrayList<>(enviados);
  }

  List<String> tipos() {
    return enviados.stream().map(Enviado::tipo).toList();
  }

  Enviado unico() {
    if (enviados.size() != 1) {
      throw new AssertionError("se esperaba un solo evento y hubo " + tipos());
    }
    return enviados.get(0);
  }

  Enviado ultimo() {
    return enviados.get(enviados.size() - 1);
  }

  void limpiar() {
    enviados.clear();
  }

  /** Los nombres de los campos de un objeto JSON, para comparar contra el contrato. */
  static Set<String> campos(JsonNode nodo) {
    Set<String> nombres = new TreeSet<>();
    Iterator<String> it = nodo.fieldNames();
    it.forEachRemaining(nombres::add);
    return nombres;
  }
}
