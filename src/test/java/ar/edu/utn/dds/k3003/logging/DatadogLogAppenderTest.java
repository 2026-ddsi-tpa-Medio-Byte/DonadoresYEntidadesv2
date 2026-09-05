package ar.edu.utn.dds.k3003.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests del appender que manda los logs a Datadog. */
class DatadogLogAppenderTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private LoggingEvent evento(String mensaje, Level nivel, Map<String, String> mdc) {
    LoggerContext ctx = new LoggerContext();
    LoggingEvent e = new LoggingEvent();
    e.setLoggerName("ar.edu.utn.dds.k3003.Fachada");
    e.setLevel(nivel);
    e.setMessage(mensaje);
    e.setTimeStamp(1700000000000L);
    e.setThreadName("http-nio-8080-exec-1");
    e.setMDCPropertyMap(mdc == null ? java.util.Map.of() : mdc);
    return e;
  }

  /** El armado del evento es privado: se llega por reflexión para no exponerlo solo por el test. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> mapear(DatadogLogAppender appender, LoggingEvent e)
      throws Exception {
    Method m = DatadogLogAppender.class.getDeclaredMethod("comoMapa", ch.qos.logback.classic.spi.ILoggingEvent.class);
    m.setAccessible(true);
    return (Map<String, Object>) m.invoke(appender, e);
  }

  @Test
  @DisplayName("El evento lleva los campos que Datadog necesita para indexarlo")
  void camposDeDatadog() throws Exception {
    DatadogLogAppender appender = new DatadogLogAppender();
    appender.setServicio("donaciones");
    appender.setAmbiente("produccion");

    Map<String, Object> m = mapear(appender, evento("Donacion registrada", Level.INFO, null));

    assertEquals("java", m.get("ddsource"));
    assertEquals("donaciones", m.get("service"));
    assertEquals("info", m.get("status"), "Datadog espera el nivel en minúscula");
    assertEquals("Donacion registrada", m.get("message"));
    assertTrue(m.get("ddtags").toString().contains("env:produccion"));
  }

  @Test
  @DisplayName("El traceId del MDC viaja en el evento: es lo que permite seguir una operación")
  void llevaElTrace() throws Exception {
    DatadogLogAppender appender = new DatadogLogAppender();

    Map<String, Object> m =
        mapear(
            appender,
            evento("Llamando a Logistica", Level.INFO, Map.of("traceId", "abc123", "ruta", "/donaciones")));

    assertEquals("abc123", m.get("traceId"));
    assertEquals("/donaciones", m.get("ruta"));
  }

  @Test
  @DisplayName("Un mensaje con comillas y saltos de línea genera JSON válido")
  void jsonValidoConCaracteresRaros() throws Exception {
    DatadogLogAppender appender = new DatadogLogAppender();

    // Construir el JSON a mano se rompía justo con esto; por eso lo arma Jackson.
    String feo = "Error: producto \"Arroz\\Fideos\"\n\tno encontrado";
    Map<String, Object> m = mapear(appender, evento(feo, Level.ERROR, null));

    String serializado = JSON.writeValueAsString(m);
    JsonNode vuelta = JSON.readTree(serializado);

    assertEquals(feo, vuelta.get("message").asText(), "el mensaje tiene que sobrevivir la ida y vuelta");
    assertEquals("error", vuelta.get("status").asText());
  }

  @Test
  @DisplayName("Sin API key el appender no arranca, pero tampoco falla")
  void sinApiKeyNoArranca() {
    DatadogLogAppender appender = new DatadogLogAppender();
    appender.setContext(new LoggerContext());

    assertDoesNotThrow(appender::start);
    assertFalse(appender.isStarted(), "sin key no debe quedar activo");

    // Y aunque le lleguen eventos, no puede explotar: la app tiene que seguir andando.
    assertDoesNotThrow(() -> appender.doAppend(evento("algo", Level.INFO, null)));
  }

  @Test
  @DisplayName("Encolar un evento nunca bloquea al hilo que está atendiendo un pedido")
  void encolarNoBloquea() {
    DatadogLogAppender appender = new DatadogLogAppender();
    appender.setContext(new LoggerContext());
    appender.setApiKey("clave-de-prueba");
    appender.setSitio("localhost:1");  // no existe: los envíos van a fallar
    appender.start();

    // Muchos más eventos que el tamaño de la cola: si bloqueara, esto no terminaría.
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(10),
        () -> {
          for (int i = 0; i < 5000; i++) {
            appender.doAppend(evento("evento " + i, Level.INFO, null));
          }
        });

    appender.stop();
  }

  private static void assertTimeoutPreemptively(java.time.Duration limite, Runnable r) {
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(limite, r::run);
  }
}
