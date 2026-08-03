package ar.edu.utn.dds.k3003.clients;

import java.util.Map;
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
 * llamadas son best-effort: si Logística no responde, la creación de la necesidad no falla.
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
   * {logistica.url}/stock/{productoID}} → {@code {"disponible": N}}. Devuelve 0 si no hay stock o si
   * Logística no responde.
   */
  public int consultarStockDisponible(String productoID) {
    String url = logisticaUrl + "/stock/" + productoID;
    log.info("[Donadores -> Logistica] Consultando stock (productoID={})", productoID);
    try {
      Map<?, ?> resp = restTemplate.getForObject(url, Map.class);
      Object disponible = resp != null ? resp.get("disponible") : null;
      int stock = disponible != null ? Integer.parseInt(disponible.toString()) : 0;
      log.info(
          "[Donadores <- Logistica] stock disponible (productoID={}, stock={})", productoID, stock);
      return stock;
    } catch (Exception e) {
      log.warn(
          "[Donadores <- Logistica] no se pudo consultar stock (productoID={}): {}",
          productoID,
          e.getMessage());
      return 0;
    }
  }

  /**
   * Solicita a Logística una asignación originada en Donadores y Entidades, para que pueda
   * diferenciarla del matchmaking. Contrato asumido: {@code POST {logistica.url}/asignaciones/solicitud}
   * con cuerpo {@code {necesidadID, productoID, cantidad, origen:"SOLICITUD_DONADORES"}}.
   */
  public void solicitarAsignacion(String necesidadID, String productoID, int cantidad) {
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
      restTemplate.postForObject(url, body, Object.class);
      log.info("[Donadores <- Logistica] asignacion solicitada OK (necesidadID={})", necesidadID);
    } catch (Exception e) {
      log.warn(
          "[Donadores <- Logistica] no se pudo solicitar asignacion (necesidadID={}): {}",
          necesidadID,
          e.getMessage());
    }
  }
}
