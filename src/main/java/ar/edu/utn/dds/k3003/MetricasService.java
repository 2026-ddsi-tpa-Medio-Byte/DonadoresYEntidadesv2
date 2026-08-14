package ar.edu.utn.dds.k3003;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MetricasService {

  /** Guardado para poder registrar métricas con tags variables. */
  private final MeterRegistry registry;

  private final Counter donadoresRegistrados;
  private final Counter donadoresErrores;
  private final Counter entidadesRegistradas;
  private final Counter quejasRegistradas;
  private final Counter necesidadesRegistradas;
  private final Counter necesidadesSatisfechas;
  private final Counter necesidadesErrores;

  public MetricasService(MeterRegistry registry) {
    this.registry = registry;

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

    this.necesidadesErrores = Counter.builder("entidades.necesidades.errores")
        .description("Necesidades rechazadas (p. ej. producto inexistente)")
        .tag("modulo", "donadores-y-entidades")
        .register(registry);
  }

  public void incrementarDonadoresRegistrados()    { donadoresRegistrados.increment(); }
  public void incrementarDonadoresErrores()        { donadoresErrores.increment(); }
  public void incrementarEntidadesRegistradas()    { entidadesRegistradas.increment(); }
  public void incrementarQuejasRegistradas()       { quejasRegistradas.increment(); }
  public void incrementarNecesidadesRegistradas()  { necesidadesRegistradas.increment(); }
  public void incrementarNecesidadesSatisfechas()  { necesidadesSatisfechas.increment(); }
  public void incrementarNecesidadesErrores()      { necesidadesErrores.increment(); }

  // ── Impacto en los otros módulos ──────────────────────────────────────────
  //
  // Los contadores de arriba dicen qué pasó dentro de este módulo. Estos dicen a quién
  // salimos a llamar por cada operación y cómo nos fue. Agrupando por la etiqueta
  // "operacion" en Datadog se ve el efecto dominó: un POST /necesidades dispara una
  // llamada a Donaciones y hasta dos a Logística.

  /**
   * Registra una llamada saliente a otro módulo.
   *
   * @param operacion qué operación nuestra la disparó, por ejemplo {@code registrar_necesidad}
   * @param destino módulo al que le pegamos: {@code donaciones} o {@code logistica}
   * @param accion qué le pedimos, por ejemplo {@code validar_producto}
   * @param exito si respondió bien
   * @param nanos cuánto tardó
   */
  public void registrarLlamadaSaliente(
      String operacion, String destino, String accion, boolean exito, long nanos) {
    Counter.builder("integracion.llamadas")
        .description("Llamadas salientes de Donadores y Entidades a otros módulos")
        .tag("modulo", "donadores-y-entidades")
        .tag("operacion", operacion)
        .tag("destino", destino)
        .tag("accion", accion)
        .tag("resultado", exito ? "ok" : "error")
        .register(registry)
        .increment();

    Timer.builder("integracion.duracion")
        .description("Cuánto tardan las llamadas salientes de Donadores y Entidades")
        .tag("modulo", "donadores-y-entidades")
        .tag("operacion", operacion)
        .tag("destino", destino)
        .tag("accion", accion)
        .register(registry)
        .record(nanos, TimeUnit.NANOSECONDS);
  }
}