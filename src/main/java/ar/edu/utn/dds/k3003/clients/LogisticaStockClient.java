package ar.edu.utn.dds.k3003.clients;

import java.util.Map;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP hacia Logística para las consultas de stock y solicitudes de asignación de la
 * Entrega 4.
 *
 * <p>NOTA: estos endpoints son parte de la funcionalidad de "stock" que el módulo de Logística debe
 * exponer en la Entrega 4. El contrato asumido está documentado en {@code docs/ENTREGA4.md}. Las
 * llamadas son best-effort: si Logística no responde, la creación de la necesidad no falla. Por eso
 * el cliente no lanza excepciones, pero sí informa si Logística contestó: la fachada lo necesita
 * para no marcar como cubierto algo que Logística no apartó, y para que la métrica diga la verdad.
 */
@Component
public class LogisticaStockClient {

  private static final Logger log = LoggerFactory.getLogger(LogisticaStockClient.class);

  @Value("${logistica.url:http://localhost:8082}")
  private String logisticaUrl;

  private final RestTemplate restTemplate;

  public LogisticaStockClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  /**
   * Consulta el stock disponible de un producto en Logística. Contrato asumido: {@code GET
   * {logistica.url}/stock/{productoID}} → {@code {"disponible": N}}.
   *
   * @return la cantidad disponible (0 si no hay stock), o vacío si Logística no contestó o contestó
   *     algo que no respeta el contrato. Antes ambos casos daban 0 y una caída de Logística se veía
   *     igual que un producto sin stock.
   */
  public OptionalInt consultarStockDisponible(String productoID) {
    String url = logisticaUrl + "/stock/" + productoID;
    log.info("[Donadores -> Logistica] Consultando stock (productoID={})", productoID);
    try {
      Map<?, ?> resp = restTemplate.getForObject(url, Map.class);
      Object disponible = resp != null ? resp.get("disponible") : null;
      if (disponible == null) {
        // Para un producto sin stock Logística contesta {"disponible": 0}; si falta el campo,
        // cambió el contrato, y asumir 0 escondería el problema.
        log.warn(
            "[Donadores <- Logistica] respuesta de stock sin el campo disponible (productoID={}): {}",
            productoID,
            resp);
        return OptionalInt.empty();
      }
      int stock =
          disponible instanceof Number numero
              ? numero.intValue()
              : Integer.parseInt(disponible.toString());
      log.info(
          "[Donadores <- Logistica] stock disponible (productoID={}, stock={})", productoID, stock);
      return OptionalInt.of(stock);
    } catch (Exception e) {
      // Incluye el 404: con el contrato vigente quiere decir que la ruta no existe, no que el
      // producto no tenga stock.
      log.warn(
          "[Donadores <- Logistica] no se pudo consultar stock (productoID={}): {}",
          productoID,
          e.getMessage());
      return OptionalInt.empty();
    }
  }

  /**
   * Solicita a Logística una asignación originada en Donadores y Entidades, para que pueda
   * diferenciarla del matchmaking. Contrato asumido: {@code POST {logistica.url}/asignaciones/solicitud}
   * con cuerpo {@code {necesidadID, productoID, cantidad, origen:"SOLICITUD_DONADORES"}}.
   *
   * @return si Logística confirmó la asignación (respondió 2xx). Solo en ese caso se puede dar por
   *     cubierta la necesidad: si no, Donadores contaría unidades que Logística nunca apartó.
   */
  public boolean solicitarAsignacion(String necesidadID, String productoID, int cantidad) {
    String url = logisticaUrl + "/asignaciones/solicitud";
    Map<String, Object> body =
        Map.of(
            "necesidadID", necesidadID,
            "productoID", productoID,
            "cantidad", cantidad,
            "origen", "SOLICITUD_DONADORES");
    log.info(
        "[Donadores -> Logistica] Solicitando asignacion (necesidadID={}, productoID={}, cantidad={})",
        necesidadID,
        productoID,
        cantidad);
    try {
      // Se lee como texto para que confirme solo el código: el formato del cuerpo no está
      // documentado, y si no se pudiera interpretar se daría por fallida una asignación que
      // Logística sí hizo. Los 4xx y 5xx llegan como excepción.
      restTemplate.postForEntity(url, body, String.class);
      log.info("[Donadores <- Logistica] asignacion solicitada OK (necesidadID={})", necesidadID);
      return true;
    } catch (Exception e) {
      log.warn(
          "[Donadores <- Logistica] no se pudo solicitar asignacion (necesidadID={}): {}",
          necesidadID,
          e.getMessage());
      return false;
    }
  }
}
