package ar.edu.utn.dds.k3003.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.utn.dds.k3003.AppConfig;
import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.MetricasService;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorStatsDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.InsigniaDTO;
import ar.edu.utn.dds.k3003.repositories.InMemoryDonadoresRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryEntidadesRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryNecesidadesRepo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Cómo traduce el cliente las respuestas de Incentivos.
 *
 * <p>Lo que importa es separar «el donador no tiene» de «Incentivos no contestó»: lo primero es una
 * respuesta válida y no puede contar como falla, lo segundo tiene que llegar a las métricas y al
 * log. Si se confunden, la alarma de integraciones suena en una demo sin ninguna falla real.
 */
class IncentivosHttpClientTest {

  private static final String URL = "http://incentivos.test";

  private MockRestServiceServer incentivos;
  private IncentivosHttpClient cliente;

  @BeforeEach
  void setUp() {
    // Con los conversores de Spring Boot, como en producción.
    RestTemplate restTemplate = new RestTemplate(new HttpMessageConverters().getConverters());
    incentivos = MockRestServiceServer.bindTo(restTemplate).build();
    cliente = new IncentivosHttpClient(restTemplate);
    ReflectionTestUtils.setField(cliente, "incentivosUrl", URL);
  }

  @Test
  @DisplayName("Un 404 en la misión actual quiere decir que no tiene misión: devuelve null")
  void sinMision() {
    incentivos
        .expect(requestTo(URL + "/donadores/7/mision-actual"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertNull(cliente.getMisionEnCursoDeDonador("7"));
    incentivos.verify();
  }

  @Test
  @DisplayName("Un 404 en las insignias quiere decir que no ganó ninguna: devuelve lista vacía")
  void sinInsignias() {
    incentivos
        .expect(requestTo(URL + "/donadores/7/insignias"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertEquals(List.of(), cliente.getInsigniasDeDonador("7"));
    incentivos.verify();
  }

  @Test
  @DisplayName("Con datos, devuelve las insignias y la misión que informa Incentivos")
  void conDatos() {
    incentivos
        .expect(requestTo(URL + "/donadores/7/insignias"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"ins-1\",\"nombre\":\"Primera\",\"descripcion\":\"primera donación\"}]",
                MediaType.APPLICATION_JSON));
    incentivos
        .expect(requestTo(URL + "/donadores/7/mision-actual"))
        .andRespond(
            withSuccess(
                "{\"id\":\"mis-1\",\"nombre\":\"Donar 5\",\"insigniaID\":\"ins-1\"}",
                MediaType.APPLICATION_JSON));

    assertEquals(
        List.of("ins-1"), cliente.getInsigniasDeDonador("7").stream().map(InsigniaDTO::id).toList());
    assertEquals("mis-1", cliente.getMisionEnCursoDeDonador("7").id());
  }

  @Test
  @DisplayName("Si Incentivos falla con un 500, las dos consultas lanzan excepción")
  void errorDelServidor() {
    incentivos
        .expect(requestTo(URL + "/donadores/7/insignias"))
        .andRespond(withServerError());
    incentivos
        .expect(requestTo(URL + "/donadores/7/mision-actual"))
        .andRespond(withServerError());

    RuntimeException insignias =
        assertThrows(RuntimeException.class, () -> cliente.getInsigniasDeDonador("7"));
    RuntimeException mision =
        assertThrows(RuntimeException.class, () -> cliente.getMisionEnCursoDeDonador("7"));

    assertTrue(
        insignias.getMessage().startsWith("Incentivos no respondió o falló"),
        insignias.getMessage());
    assertTrue(
        mision.getMessage().startsWith("Incentivos no respondió o falló"), mision.getMessage());
  }

  @Test
  @DisplayName("Si Incentivos no contesta a tiempo, la consulta lanza excepción")
  void sinRespuesta() {
    incentivos
        .expect(requestTo(URL + "/donadores/7/mision-actual"))
        .andRespond(
            pedido -> {
              throw new SocketTimeoutException("Read timed out");
            });

    RuntimeException e =
        assertThrows(RuntimeException.class, () -> cliente.getMisionEnCursoDeDonador("7"));

    assertTrue(e.getMessage().startsWith("Incentivos no respondió o falló"), e.getMessage());
  }

  /** Lo mismo visto desde la fachada: es lo que termina en la alarma de Datadog. */
  @Test
  @DisplayName("En las estadísticas, un 404 de Incentivos cuenta como exitosa y un 500 como fallida")
  void estadisticasCuentanSoloLasFallasReales() {
    SimpleMeterRegistry registro = new SimpleMeterRegistry();
    Fachada fachada =
        new Fachada(
            new InMemoryDonadoresRepo(),
            new InMemoryEntidadesRepo(),
            new InMemoryNecesidadesRepo(),
            new MetricasService(registro));
    fachada.setFachadaIncentivos(cliente);
    String id =
        fachada
            .agregarDonador(
                new DonadorDTO(
                    null, "Ana", "Pérez", 30, "ana@mail.com", "30111222", "Calle 1", null, null))
            .id();

    // Primera consulta: el donador todavía no tiene nada en Incentivos.
    incentivos
        .expect(requestTo(URL + "/donadores/" + id + "/insignias"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    incentivos
        .expect(requestTo(URL + "/donadores/" + id + "/mision-actual"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    // Segunda consulta: Incentivos está caído.
    incentivos
        .expect(requestTo(URL + "/donadores/" + id + "/insignias"))
        .andRespond(withServerError());
    incentivos
        .expect(requestTo(URL + "/donadores/" + id + "/mision-actual"))
        .andRespond(withServerError());

    DonadorStatsDTO sinNada = fachada.estadisticasDonador(id);
    assertTrue(sinNada.insigniasID().isEmpty());
    assertNull(sinNada.misionActualID());
    assertEquals(2, llamadas(registro, "ok"), "«no tiene» es una respuesta, no una falla");
    assertEquals(0, llamadas(registro, "error"));

    DonadorStatsDTO conIncentivosCaido = fachada.estadisticasDonador(id);
    assertEquals("Ana", conIncentivosCaido.nombre());
    assertEquals(2, llamadas(registro, "error"), "«no contestó» sí es una falla");
    incentivos.verify();
  }

  // ── Contra un Incentivos con HTTP de verdad ──────────────────────────────────────────────────
  //
  // Los tests de arriba simulan la red, y con eso un timeout nunca llega a pasar. Estos usan el
  // RestTemplate que arma AppConfig para Incentivos contra un servidor local, para probar lo que
  // falló en producción: con Incentivos colgado, la consulta esperaba para siempre.

  /** Chico para que el test sea rápido; en producción son 3 s de conexión y 8 s de lectura. */
  private static final long TIMEOUT_MS = 1000;

  private IncentivosDePrueba incentivosReal;

  @AfterEach
  void apagarIncentivosReal() {
    if (incentivosReal != null) {
      incentivosReal.close();
    }
  }

  @Test
  // Sin timeout en el cliente, la consulta queda esperando: este límite hace que el test falle en
  // vez de colgar la suite. En otro hilo, porque una lectura bloqueada puede no atender la
  // interrupción y JUnit no podría cortarla.
  @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  @DisplayName(
      "Si Incentivos acepta la conexión y no contesta, la consulta corta al vencer el timeout de"
          + " lectura y lanza excepción, en vez de quedar colgada")
  void incentivosColgadoCortaPorTimeout() {
    incentivosReal = IncentivosDePrueba.queNoContesta();
    IncentivosHttpClient clienteReal = clienteContra(incentivosReal);

    long inicio = System.nanoTime();
    RuntimeException e =
        assertThrows(RuntimeException.class, () -> clienteReal.getMisionEnCursoDeDonador("7"));
    long transcurridoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

    assertTrue(
        transcurridoMs < 2 * TIMEOUT_MS,
        "tardó " + transcurridoMs + " ms con un timeout de " + TIMEOUT_MS + " ms");
    // El timeout llega como ResourceAccessException, que es un RestClientException: el cliente lo
    // traduce igual que un 500, y por eso la fachada lo cuenta como llamada fallida.
    assertInstanceOf(ResourceAccessException.class, e.getCause());
    assertTrue(e.getMessage().startsWith("Incentivos no respondió o falló"), e.getMessage());
    // El pedido llegó: cortó por la lectura, no porque no se pudiera conectar.
    assertEquals(List.of("/donadores/7/mision-actual"), incentivosReal.rutasPedidas());
  }

  @Test
  @DisplayName(
      "El RestTemplate de Incentivos manda el X-Trace-Id del MDC, para seguir la consulta en el"
          + " log centralizado")
  void propagaElTraceId() {
    incentivosReal = IncentivosDePrueba.queContesta("[]");
    IncentivosHttpClient clienteReal = clienteContra(incentivosReal);

    MDC.put("traceId", "traza-estadisticas");
    try {
      assertEquals(List.of(), clienteReal.getInsigniasDeDonador("7"));
    } finally {
      MDC.remove("traceId");
    }

    assertEquals(List.of("traza-estadisticas"), incentivosReal.traceIdsRecibidos());
  }

  /** El cliente con el RestTemplate que usa en producción, apuntado al servidor local. */
  private IncentivosHttpClient clienteContra(IncentivosDePrueba servidor) {
    // El builder con los conversores de Spring Boot, como el que inyecta Spring.
    RestTemplateBuilder builder =
        new RestTemplateBuilder().messageConverters(new HttpMessageConverters().getConverters());
    IncentivosHttpClient clienteReal =
        new IncentivosHttpClient(
            new AppConfig().restTemplateIncentivos(builder, TIMEOUT_MS, TIMEOUT_MS));
    ReflectionTestUtils.setField(clienteReal, "incentivosUrl", servidor.url());
    return clienteReal;
  }

  private double llamadas(SimpleMeterRegistry registro, String resultado) {
    return registro.find("integracion.llamadas")
        .tag("destino", "incentivos")
        .tag("resultado", resultado)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }
}
