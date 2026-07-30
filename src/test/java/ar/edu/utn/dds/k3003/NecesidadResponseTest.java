package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.controllers.responses.NecesidadResponse;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.repositories.DonadoresYEntidadesDataMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica que el response de necesidades expone cantidadActual, campo que consume Logística para
 * el algoritmo de matchmaking.
 */
class NecesidadResponseTest {

  private final DonadoresYEntidadesDataMapper mapper = new DonadoresYEntidadesDataMapper();

  private NecesidadMaterial necesidadDeEjemplo() {
    return new NecesidadMaterial(
        "1", "1", 8, "30 sillas tras inundacion", 30, "7",
        TipoNecesidadMaterialEnum.EXTRAORDINARIA);
  }

  @Test
  @DisplayName("Una necesidad nueva expone cantidadActual = 0")
  void necesidadNuevaTieneCantidadActualCero() {
    NecesidadResponse resp = mapper.toNecesidadResponse(necesidadDeEjemplo());

    assertEquals(0, resp.cantidadActual());
    assertEquals(30, resp.cantidadObjetivo());
  }

  @Test
  @DisplayName("Tras satisfacer parcialmente, cantidadActual refleja lo cubierto")
  void cantidadActualRefleja() {
    NecesidadMaterial necesidad = necesidadDeEjemplo();
    necesidad.registrarSatisfaccion(10);

    NecesidadResponse resp = mapper.toNecesidadResponse(necesidad);

    assertEquals(10, resp.cantidadActual());
    assertEquals(30, resp.cantidadObjetivo());
  }

  @Test
  @DisplayName("El JSON serializado incluye el campo cantidadActual")
  void jsonIncluyeCantidadActual() throws Exception {
    NecesidadMaterial necesidad = necesidadDeEjemplo();
    necesidad.registrarSatisfaccion(10);

    String json = new ObjectMapper().writeValueAsString(mapper.toNecesidadResponse(necesidad));

    assertTrue(json.contains("\"cantidadActual\":10"), "falta cantidadActual en: " + json);
    assertTrue(json.contains("\"cantidadObjetivo\":30"), json);
    assertTrue(json.contains("\"productoSolicitadoID\":\"7\""), json);
    assertTrue(json.contains("\"tipo\":\"EXTRAORDINARIA\""), json);
  }
}
