package ar.edu.utn.dds.k3003.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Le pone un identificador a cada pedido para poder seguirlo en el log centralizado.
 *
 * <p>El {@code traceId} es lo importante: si llega en el header lo reutiliza, y si no lo genera.
 * Como los módulos se pasan ese header entre sí, una donación que toca Donaciones, Donadores y
 * Logística deja logs con el mismo traceId en los tres. En Datadog se filtra por ese valor y
 * aparece el recorrido completo de la operación, aunque cada módulo corra en su propio servicio.
 */
@Component
public class TrazabilidadFilter extends OncePerRequestFilter {

  /** Header con el que los módulos se pasan el identificador de la operación. */
  public static final String HEADER_TRACE = "X-Trace-Id";

  private static final Logger log = LoggerFactory.getLogger(TrazabilidadFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws ServletException, IOException {

    String traceId = req.getHeader(HEADER_TRACE);
    boolean naceAca = traceId == null || traceId.isBlank();
    if (naceAca) {
      traceId = UUID.randomUUID().toString().substring(0, 12);
    }

    MDC.put("traceId", traceId);
    MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
    MDC.put("metodo", req.getMethod());
    MDC.put("ruta", req.getRequestURI());

    // Que viaje también en la respuesta: así quien llamó puede correlacionar.
    resp.setHeader(HEADER_TRACE, traceId);

    long inicio = System.currentTimeMillis();
    try {
      chain.doFilter(req, resp);
    } finally {
      long duracion = System.currentTimeMillis() - inicio;
      MDC.put("duracionMs", String.valueOf(duracion));
      MDC.put("status", String.valueOf(resp.getStatus()));
      log.info(
          "{} {} -> {} en {}ms",
          req.getMethod(),
          req.getRequestURI(),
          resp.getStatus(),
          duracion);
      MDC.clear();
    }
  }

  /** No tiene sentido loguear los pedidos de Actuator ni del Swagger: son ruido. */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    String ruta = req.getRequestURI();
    return ruta.startsWith("/actuator")
        || ruta.startsWith("/swagger")
        || ruta.startsWith("/api-docs")
        || ruta.startsWith("/v3/api-docs");
  }
}
