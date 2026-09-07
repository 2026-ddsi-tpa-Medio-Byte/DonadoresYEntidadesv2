package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.repositories.DonadoresRepository;
import ar.edu.utn.dds.k3003.repositories.NecesidadesRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Métricas que describen el estado del dominio, no la actividad.
 *
 * <p>Los contadores de {@link MetricasService} dicen cuántas veces pasó algo desde que arrancó el
 * servicio: útiles para ver movimiento, inútiles para saber cómo está el sistema ahora. Estas son
 * medidores: se evalúan en el momento en que Datadog las consulta y responden preguntas como
 * cuántas necesidades siguen sin cubrirse o cuántos donadores están bloqueados.
 *
 * <p>Son las que sostienen las alarmas de negocio. Un contador de necesidades satisfechas no
 * puede avisar que hay treinta esperando hace días; un medidor sí.
 *
 * <p>Guardado por null: con el constructor sin argumentos que usan los tests de cátedra no hay
 * repositorios, y sin ellos no se registra ninguna métrica.
 */
@Component
public class MetricasDominio {

  private final MeterRegistry registry;
  private final NecesidadesRepository necesidades;
  private final DonadoresRepository donadores;

  @Autowired(required = false)
  public MetricasDominio(
      MeterRegistry registry,
      NecesidadesRepository necesidades,
      DonadoresRepository donadores) {
    this.registry = registry;
    this.necesidades = necesidades;
    this.donadores = donadores;
  }

  @PostConstruct
  void registrar() {
    if (registry == null || necesidades == null || donadores == null) {
      return;
    }

    Gauge.builder("dominio.necesidades.insatisfechas", this, m -> m.contarInsatisfechas())
        .description("Necesidades que todavía no se cubrieron del todo")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);

    Gauge.builder("dominio.necesidades.unidades_faltantes", this, m -> m.unidadesFaltantes())
        .description("Cuántas unidades hacen falta en total para cubrir todo lo pendiente")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);

    Gauge.builder("dominio.necesidades.cobertura_promedio", this, m -> m.coberturaPromedio())
        .description("Porcentaje promedio de cobertura de las necesidades sin satisfacer")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);

    // Un medidor por estado, en vez de uno solo con el estado adentro: así se puede
    // graficar cada uno por separado y alarmar sobre los baneados sin tocar el resto.
    for (EstadoDonadorEnum estado : EstadoDonadorEnum.values()) {
      Gauge.builder("dominio.donadores.por_estado", this, m -> m.contarDonadoresEn(estado))
          .description("Donadores en cada estado de reputación")
          .tag("modulo", "donadores-y-entidades")
          .tag("estado", estado.name().toLowerCase())
          .register(registry);
    }
  }

  // ── Cálculos ───────────────────────────────────────────────────────────────

  double contarInsatisfechas() {
    return todas().stream().filter(n -> !Boolean.TRUE.equals(n.estaSatisfecha())).count();
  }

  double unidadesFaltantes() {
    return todas().stream()
        .filter(n -> !Boolean.TRUE.equals(n.estaSatisfecha()))
        .mapToInt(n -> n.cantidadFaltante() == null ? 0 : n.cantidadFaltante())
        .sum();
  }

  /**
   * Promedio de cobertura de lo que sigue pendiente.
   *
   * <p>Se mide solo sobre las insatisfechas a propósito: si entraran las completas, el promedio
   * subiría con cada necesidad cerrada y taparía que las abiertas están estancadas.
   */
  double coberturaPromedio() {
    List<NecesidadMaterial> pendientes =
        todas().stream().filter(n -> !Boolean.TRUE.equals(n.estaSatisfecha())).toList();
    if (pendientes.isEmpty()) {
      return 100.0;
    }
    double suma = 0;
    for (NecesidadMaterial n : pendientes) {
      int objetivo = n.getCantidadObjetivo() == null ? 0 : n.getCantidadObjetivo();
      int falta = n.cantidadFaltante() == null ? 0 : n.cantidadFaltante();
      if (objetivo > 0) {
        suma += (objetivo - falta) * 100.0 / objetivo;
      }
    }
    return suma / pendientes.size();
  }

  double contarDonadoresEn(EstadoDonadorEnum estado) {
    try {
      return donadores.findAll().stream().filter(d -> estado.equals(d.getEstado())).count();
    } catch (RuntimeException e) {
      return 0;
    }
  }

  /** Si la base falla, la métrica devuelve cero en vez de tumbar el scrape de Datadog. */
  private List<NecesidadMaterial> todas() {
    try {
      return necesidades.findAll();
    } catch (RuntimeException e) {
      return List.of();
    }
  }
}
