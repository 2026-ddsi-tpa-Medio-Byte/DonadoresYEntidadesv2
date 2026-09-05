package ar.edu.utn.dds.k3003.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manda los logs de la aplicación a Datadog.
 *
 * <p>La cátedra da un ejemplo con Better Stack, que usa un appender de terceros. Acá se manda a
 * Datadog, que es donde ya viven las métricas del grupo, así los logs y las métricas quedan en el
 * mismo lugar y se pueden correlacionar. No hay appender oficial de Datadog para Logback, así que
 * este hace el POST a la API de ingesta.
 *
 * <p>Tres decisiones que importan:
 *
 * <ul>
 *   <li><b>Envío en lote y en segundo plano.</b> Un POST por línea de log haría que cada operación
 *       de negocio esperara por la red. Los eventos se encolan y un hilo aparte los manda cada
 *       pocos segundos.
 *   <li><b>Nunca frena la aplicación.</b> Si Datadog no responde o la cola se llena, se descartan
 *       eventos y se sigue. Un problema de logging no puede tirar abajo una donación.
 *   <li><b>El JSON lo arma Jackson.</b> Construirlo a mano obliga a escapar comillas, saltos de
 *       línea y unicode; cualquier descuido genera un cuerpo inválido que Datadog rechaza entero.
 * </ul>
 */
public class DatadogLogAppender extends AppenderBase<ILoggingEvent> {

  private static final int MAX_EN_COLA = 2000;
  private static final int MAX_POR_LOTE = 100;
  private static final Duration CADA = Duration.ofSeconds(5);

  private static final ObjectMapper JSON = new ObjectMapper();

  private final BlockingQueue<ILoggingEvent> cola = new LinkedBlockingQueue<>(MAX_EN_COLA);
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private Thread enviador;
  private volatile boolean activo = true;

  // Configurables desde logback-spring.xml
  private String apiKey;
  private String servicio = "donatrack";
  private String ambiente = "produccion";
  private String sitio = "us5.datadoghq.com";

  @Override
  public void start() {
    if (apiKey == null || apiKey.isBlank()) {
      addWarn("Sin DATADOG_API_KEY: los logs quedan solo en consola.");
      return;
    }
    enviador = new Thread(this::bucleDeEnvio, "datadog-logs");
    enviador.setDaemon(true);
    enviador.start();
    super.start();
    addInfo("Enviando logs a Datadog como servicio '" + servicio + "'");
  }

  @Override
  public void stop() {
    activo = false;
    if (enviador != null) {
      enviador.interrupt();
    }
    super.stop();
  }

  @Override
  protected void append(ILoggingEvent evento) {
    // offer y no put: si la cola está llena preferimos perder logs antes que bloquear
    // el hilo que está atendiendo una donación.
    cola.offer(evento);
  }

  private void bucleDeEnvio() {
    List<ILoggingEvent> lote = new ArrayList<>(MAX_POR_LOTE);
    while (activo) {
      try {
        ILoggingEvent primero = cola.poll(CADA.toMillis(), TimeUnit.MILLISECONDS);
        if (primero == null) {
          continue;
        }
        lote.clear();
        lote.add(primero);
        cola.drainTo(lote, MAX_POR_LOTE - 1);
        enviar(lote);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        addWarn("No se pudieron enviar logs a Datadog: " + e.getMessage());
      }
    }
  }

  private void enviar(List<ILoggingEvent> lote) {
    List<Map<String, Object>> cuerpo = new ArrayList<>(lote.size());
    for (ILoggingEvent evento : lote) {
      cuerpo.add(comoMapa(evento));
    }

    String json;
    try {
      json = JSON.writeValueAsString(cuerpo);
    } catch (Exception e) {
      addWarn("No se pudo serializar el lote de logs: " + e.getMessage());
      return;
    }

    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create("https://http-intake.logs." + sitio + "/api/v2/logs"))
            .header("Content-Type", "application/json")
            .header("DD-API-KEY", apiKey)
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 300) {
        addWarn("Datadog rechazó el lote (" + resp.statusCode() + "): " + resp.body());
      }
    } catch (Exception e) {
      addWarn("Falló el envío a Datadog: " + e.getMessage());
    }
  }

  /** Arma el evento con los nombres de campo que Datadog reconoce. */
  private Map<String, Object> comoMapa(ILoggingEvent e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ddsource", "java");
    m.put("service", servicio);
    m.put("ddtags", "env:" + ambiente + ",modulo:" + servicio);
    m.put("status", e.getLevel().toString().toLowerCase());
    m.put("logger", e.getLoggerName());
    m.put("thread", e.getThreadName());
    m.put("message", e.getFormattedMessage());
    m.put("timestamp", e.getTimeStamp());

    // El MDC trae el traceId y el requestId: es lo que permite seguir una misma
    // operación de negocio a través de los distintos módulos.
    //
    // Va protegido porque getMDCPropertyMap() falla si el evento no tiene contexto de
    // Logback. No pasa en el uso normal, pero este appender no puede lanzar nunca: si
    // explota acá, se lleva puesto el hilo que está atendiendo una donación.
    try {
      Map<String, String> mdc = e.getMDCPropertyMap();
      if (mdc != null) {
        m.putAll(mdc);
      }
    } catch (RuntimeException ignorado) {
      // sin MDC el log igual sirve, solo pierde la trazabilidad
    }

    IThrowableProxy error = e.getThrowableProxy();
    if (error != null) {
      Map<String, Object> detalle = new LinkedHashMap<>();
      detalle.put("kind", error.getClassName());
      detalle.put("message", error.getMessage());
      detalle.put("stack", ThrowableProxyUtil.asString(error));
      m.put("error", detalle);
    }
    return m;
  }

  // ── Setters que lee logback-spring.xml ─────────────────────────────────────

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public void setServicio(String servicio) {
    this.servicio = servicio;
  }

  public void setAmbiente(String ambiente) {
    this.ambiente = ambiente;
  }

  public void setSitio(String sitio) {
    this.sitio = sitio;
  }
}
