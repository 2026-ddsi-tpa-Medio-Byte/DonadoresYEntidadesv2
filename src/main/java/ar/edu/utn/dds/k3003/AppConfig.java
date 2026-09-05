package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.logging.TrazabilidadFilter;
import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * RestTemplate que arrastra el identificador de la operación a los otros módulos.
     *
     * <p>Sin esto, cada módulo generaría su propio traceId y en el log centralizado una necesidad
     * se vería como tres operaciones sueltas. Con el header, Donadores, Donaciones y Logística
     * dejan logs con el mismo traceId y se puede reconstruir el recorrido completo.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .additionalInterceptors(
                        (request, body, ejecucion) -> {
                            String traceId = MDC.get("traceId");
                            if (traceId != null && !traceId.isBlank()) {
                                request.getHeaders().add(TrazabilidadFilter.HEADER_TRACE, traceId);
                            }
                            return ejecucion.execute(request, body);
                        })
                .build();
    }
}
