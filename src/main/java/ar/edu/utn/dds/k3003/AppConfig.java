package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.logging.TrazabilidadFilter;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * RestTemplate para Donaciones y Logística. Es el que se inyecta cuando no se pide otro.
     *
     * <p>No tiene timeout a propósito: al crear una necesidad, la validación del producto contra
     * Donaciones es bloqueante, y con un límite corto se rechazarían necesidades válidas mientras
     * Donaciones despierta en Render, que puede tardar cerca de un minuto.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.additionalInterceptors(AppConfig::propagarTraceId).build();
    }

    /**
     * RestTemplate solo para Incentivos, con límite de espera.
     *
     * <p>Sin límite, las estadísticas de un donador no respondían nunca con Incentivos caído: la
     * llamada quedaba esperando y la fachada nunca llegaba a responder con los datos propios. Con
     * los valores por defecto, el peor caso son dos lecturas de 8 s en serie, unos 16 s: menos que
     * los 25 s con los que el MCP corta cada pedido. Acá un timeout no rechaza nada: si Incentivos
     * no contesta, las estadísticas salen igual, sin insignias ni misión.
     */
    @Bean
    public RestTemplate restTemplateIncentivos(
            RestTemplateBuilder builder,
            @Value("${incentivos.timeout.conexion-ms:3000}") long conexionMs,
            @Value("${incentivos.timeout.lectura-ms:8000}") long lecturaMs) {
        return builder
                .additionalInterceptors(AppConfig::propagarTraceId)
                .connectTimeout(Duration.ofMillis(conexionMs))
                .readTimeout(Duration.ofMillis(lecturaMs))
                .build();
    }

    /**
     * Arrastra el identificador de la operación a los otros módulos.
     *
     * <p>Sin esto, cada módulo generaría su propio traceId y en el log centralizado una necesidad
     * se vería como tres operaciones sueltas. Con el header, Donadores, Donaciones y Logística
     * dejan logs con el mismo traceId y se puede reconstruir el recorrido completo.
     */
    private static ClientHttpResponse propagarTraceId(
            HttpRequest request, byte[] body, ClientHttpRequestExecution ejecucion)
            throws IOException {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            request.getHeaders().add(TrazabilidadFilter.HEADER_TRACE, traceId);
        }
        return ejecucion.execute(request, body);
    }
}
