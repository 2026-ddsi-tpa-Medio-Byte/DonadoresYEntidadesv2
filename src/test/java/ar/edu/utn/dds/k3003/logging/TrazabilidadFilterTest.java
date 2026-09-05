package ar.edu.utn.dds.k3003.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Tests del filtro que hace posible seguir una operación entre módulos. */
class TrazabilidadFilterTest {

  private final TrazabilidadFilter filtro = new TrazabilidadFilter();

  @Test
  @DisplayName("Si el pedido no trae traceId, se genera uno")
  void generaTraceCuandoNoViene() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/necesidades");
    MockHttpServletResponse resp = new MockHttpServletResponse();

    filtro.doFilter(req, resp, (a, b) -> {});

    String trace = resp.getHeader(TrazabilidadFilter.HEADER_TRACE);
    assertNotNull(trace);
    assertTrue(trace.length() >= 8);
  }

  @Test
  @DisplayName("Si el pedido trae traceId, se respeta: es lo que une el flujo entre módulos")
  void respetaElTraceQueLlega() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/necesidades");
    req.addHeader(TrazabilidadFilter.HEADER_TRACE, "trace-de-donadores");
    MockHttpServletResponse resp = new MockHttpServletResponse();

    filtro.doFilter(req, resp, (a, b) -> {});

    assertEquals("trace-de-donadores", resp.getHeader(TrazabilidadFilter.HEADER_TRACE));
  }

  @Test
  @DisplayName("Durante el pedido el traceId está en el MDC, y después se limpia")
  void ponerYLimpiarElMdc() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/necesidades/1");
    req.addHeader(TrazabilidadFilter.HEADER_TRACE, "abc123");
    MockHttpServletResponse resp = new MockHttpServletResponse();

    FilterChain verificarDentro =
        (a, b) -> {
          assertEquals("abc123", MDC.get("traceId"), "el log de adentro tiene que verlo");
          assertEquals("/necesidades/1", MDC.get("ruta"));
        };

    filtro.doFilter(req, resp, verificarDentro);

    // Si no se limpiara, el próximo pedido que reutilice el hilo heredaría este trace
    // y en Datadog se mezclarían dos operaciones distintas.
    assertNull(MDC.get("traceId"), "el MDC tiene que quedar limpio al terminar");
  }

  @Test
  @DisplayName("Actuator y Swagger no se loguean: son ruido")
  void ignoraLoQueNoEsNegocio() {
    for (String ruta : new String[] {"/actuator/health", "/swagger-ui/index.html", "/api-docs"}) {
      MockHttpServletRequest req = new MockHttpServletRequest("GET", ruta);
      assertTrue(filtro.shouldNotFilter(req), ruta + " no debería loguearse");
    }
  }

  @Test
  @DisplayName("Los endpoints de negocio sí se loguean")
  void loguearLoQueImporta() {
    for (String ruta : new String[] {"/donadores", "/necesidades", "/donadores/1/quejas"}) {
      MockHttpServletRequest req = new MockHttpServletRequest("POST", ruta);
      org.junit.jupiter.api.Assertions.assertFalse(
          filtro.shouldNotFilter(req), ruta + " debería loguearse");
    }
  }
}
