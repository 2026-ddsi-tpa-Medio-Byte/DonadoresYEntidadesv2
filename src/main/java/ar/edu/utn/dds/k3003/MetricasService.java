package ar.edu.utn.dds.k3003;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class MetricasService {

  private final Counter donadoresRegistrados;
  private final Counter donadoresErrores;
  private final Counter entidadesRegistradas;
  private final Counter quejasRegistradas;
  private final Counter necesidadesRegistradas;
  private final Counter necesidadesSatisfechas;

  public MetricasService(MeterRegistry registry) {
    this.donadoresRegistrados = Counter.builder("donadores.registrados")
        .description("Donadores registrados en el sistema")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);

    this.donadoresErrores = Counter.builder("donadores.errores")
        .description("Errores en operaciones de donadores")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);

    this.entidadesRegistradas = Counter.builder("entidades.registradas")
        .description("Entidades benéficas registradas")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);

    this.quejasRegistradas = Counter.builder("donadores.quejas")
        .description("Quejas registradas")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);

    this.necesidadesRegistradas = Counter.builder("entidades.necesidades.registradas")
        .description("Necesidades materiales registradas")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);

    this.necesidadesSatisfechas = Counter.builder("entidades.necesidades.satisfechas")
        .description("Necesidades materiales satisfechas")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);
  }

  public void incrementarDonadoresRegistrados() { donadoresRegistrados.increment(); }
  public void incrementarDonadoresErrores() { donadoresErrores.increment(); }
  public void incrementarEntidadesRegistradas() { entidadesRegistradas.increment(); }
  public void incrementarQuejasRegistradas() { quejasRegistradas.increment(); }
  public void incrementarNecesidadesRegistradas() { necesidadesRegistradas.increment(); }
  public void incrementarNecesidadesSatisfechas() { necesidadesSatisfechas.increment(); }
}
