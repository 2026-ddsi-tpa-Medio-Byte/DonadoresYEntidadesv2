package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.repositories.DonadoresRepository;
import ar.edu.utn.dds.k3003.repositories.NecesidadesRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests de las métricas que describen el estado del dominio. */
@ExtendWith(MockitoExtension.class)
class MetricasDominioTest {

  @Mock private NecesidadesRepository necesidades;
  @Mock private DonadoresRepository donadores;

  private MeterRegistry registry;
  private MetricasDominio metricas;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metricas = new MetricasDominio(registry, necesidades, donadores);
  }

  /** Crea una necesidad con un objetivo y una parte ya cubierta. */
  private NecesidadMaterial necesidad(int objetivo, int cubierto) {
    NecesidadMaterial n =
        new NecesidadMaterial(
            null, "1", 5, "una necesidad", objetivo, "1",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);
    if (cubierto > 0) {
      n.registrarSatisfaccion(cubierto);
    }
    return n;
  }

  @Test
  @DisplayName("Cuenta las necesidades que todavía no se cubrieron")
  void cuentaInsatisfechas() {
    when(necesidades.findAll())
        .thenReturn(List.of(necesidad(10, 10), necesidad(20, 5), necesidad(8, 0)));

    assertEquals(2, metricas.contarInsatisfechas());
  }

  @Test
  @DisplayName("Suma cuántas unidades faltan en total")
  void unidadesFaltantes() {
    when(necesidades.findAll())
        .thenReturn(
            List.of(
                necesidad(20, 5),   // faltan 15
                necesidad(8, 0),    // faltan 8
                necesidad(10, 10))); // completa, no suma

    assertEquals(23, metricas.unidadesFaltantes());
  }

  @Test
  @DisplayName("La cobertura promedio mira solo las pendientes")
  void coberturaSoloDeLasPendientes() {
    when(necesidades.findAll())
        .thenReturn(
            List.of(
                necesidad(10, 5),    // 50%
                necesidad(20, 15),   // 75%
                // si la completa entrara al promedio daría 75%, y taparía que las
                // abiertas están a mitad de camino
                necesidad(100, 100)));

    assertEquals(62.5, metricas.coberturaPromedio(), 0.01);
  }

  @Test
  @DisplayName("Sin necesidades pendientes la cobertura es 100, no una división por cero")
  void coberturaSinPendientes() {
    when(necesidades.findAll()).thenReturn(List.of(necesidad(10, 10)));

    assertEquals(100.0, metricas.coberturaPromedio(), 0.01);
  }

  @Test
  @DisplayName("Si la base falla, la métrica devuelve cero en vez de tumbar el scrape")
  void baseCaida() {
    when(necesidades.findAll()).thenThrow(new RuntimeException("la base no responde"));

    assertEquals(0, metricas.contarInsatisfechas());
    assertEquals(0, metricas.unidadesFaltantes());
  }

  @Test
  @DisplayName("Hay un medidor por cada estado de donador")
  void medidorPorEstado() {
    metricas.registrar();

    List<String> estados =
        registry.find("dominio.donadores.por_estado").gauges().stream()
            .map(g -> g.getId().getTag("estado"))
            .sorted()
            .toList();

    assertEquals(EstadoDonadorEnum.values().length, estados.size());
    org.junit.jupiter.api.Assertions.assertTrue(estados.contains("baneado"));
  }

  @Test
  @DisplayName("Sin repositorios no registra nada: es el caso de los tests de cátedra")
  void sinRepositoriosNoRegistra() {
    MeterRegistry vacio = new SimpleMeterRegistry();
    new MetricasDominio(vacio, null, null).registrar();

    assertEquals(0, vacio.getMeters().size());
  }
}
