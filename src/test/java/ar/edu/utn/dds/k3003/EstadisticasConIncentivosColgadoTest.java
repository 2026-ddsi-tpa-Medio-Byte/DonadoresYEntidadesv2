package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorStatsDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.clients.IncentivosDePrueba;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Flujo 6 (estadísticas de un donador) con Incentivos colgado, de punta a punta.
 *
 * <p>En producción, con Incentivos caído, {@code GET /donadores/{id}/estadisticas} no respondía
 * nunca: el RestTemplate no tenía timeout, la llamada quedaba esperando y el manejo de fallos de la
 * fachada no llegaba a ejecutarse. Desde el MCP, que corta a los 25 s, el flujo fallaba siempre.
 *
 * <p>Levanta la app real a propósito: además del timeout, así queda cubierto que Spring le inyecte
 * al cliente de Incentivos su RestTemplate con timeout y no el compartido, que no lo tiene.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // Chicos para que el test sea rápido; en producción son 3 s y 8 s.
      "incentivos.timeout.conexion-ms=500",
      "incentivos.timeout.lectura-ms=500"
    })
@ActiveProfiles("test")
class EstadisticasConIncentivosColgadoTest {

  private static final IncentivosDePrueba incentivos = IncentivosDePrueba.queNoContesta();

  @DynamicPropertySource
  static void apuntarAIncentivos(DynamicPropertyRegistry propiedades) {
    propiedades.add("incentivos.url", incentivos::url);
  }

  @AfterAll
  static void apagarIncentivos() {
    incentivos.close();
  }

  @Autowired private TestRestTemplate api;

  @Autowired private MeterRegistry registro;

  @Test
  // Sin timeout en el cliente, el pedido queda esperando a Incentivos: este límite hace que el
  // test falle en vez de colgar la suite.
  @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  @DisplayName(
      "Con Incentivos colgado, las estadísticas responden con los datos propios del donador y"
          + " cuentan las dos consultas a Incentivos como fallidas")
  void incentivosColgado() {
    String id =
        api.postForEntity(
                "/donadores",
                new DonadorDTO(
                    null, "Ana", "Pérez", 30, "ana@mail.com", "doc-" + System.nanoTime(),
                    "Calle 1", null, null),
                DonadorDTO.class)
            .getBody()
            .id();
    double fallidasAntes = llamadasAIncentivos("error");

    ResponseEntity<DonadorStatsDTO> resp =
        api.getForEntity("/donadores/" + id + "/estadisticas", DonadorStatsDTO.class);

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    DonadorStatsDTO stats = resp.getBody();
    assertEquals(id, stats.id());
    assertEquals("Ana", stats.nombre());
    assertEquals("Pérez", stats.apellido());
    assertEquals(EstadoDonadorEnum.VERIFICADO, stats.estado());
    assertTrue(stats.insigniasID().isEmpty(), "sin respuesta de Incentivos no hay insignias");
    assertNull(stats.misionActualID());
    assertEquals(2, llamadasAIncentivos("error") - fallidasAntes, "insignias y misión, fallidas");
    // Las dos consultas llegaron a Incentivos: se cortaron por timeout, no se saltearon.
    assertEquals(
        List.of("/donadores/" + id + "/insignias", "/donadores/" + id + "/mision-actual"),
        incentivos.rutasPedidas());
  }

  private double llamadasAIncentivos(String resultado) {
    return registro.find("integracion.llamadas")
        .tag("operacion", "estadisticas_donador")
        .tag("destino", "incentivos")
        .tag("resultado", resultado)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }
}
