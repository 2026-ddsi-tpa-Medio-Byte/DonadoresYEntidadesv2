package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Test de integración del FLUJO DE LA ENTREGA 4 (módulo Donadores y Entidades):
 *
 * <p>"Al crear una nueva necesidad, primero se corrobora con Donaciones si el producto es válido.
 * Luego se consulta a Logística si existe stock disponible; en caso afirmativo se asigna al
 * momento la cantidad disponible/necesaria."
 *
 * <p>Levanta la app real (Tomcat + H2) y simula los otros dos módulos con MockRestServiceServer
 * interceptando el RestTemplate de la app: verifica que las llamadas HTTP salientes a Donaciones y
 * Logística ocurren exactamente como pide la consigna.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Entrega4FlujoIntegrationTest {

  @Autowired private TestRestTemplate api; // llamadas ENTRANTES (como un cliente / el bot)

  @Autowired private RestTemplate restTemplate; // el que usa la app para llamadas SALIENTES

  private MockRestServiceServer otrosModulos; // simula Donaciones (8081) y Logística (8082)

  @BeforeEach
  void setUp() {
    otrosModulos = MockRestServiceServer.bindTo(restTemplate).build();
  }

  private String crearEntidad() {
    ResponseEntity<EntidadBeneficaDTO> resp =
        api.postForEntity(
            "/entidades",
            new EntidadBeneficaDTO(null, "Comedor Hogwarts", "Calle 1", "11-3000", "h@mail.com"),
            EntidadBeneficaDTO.class);
    assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    return resp.getBody().id();
  }

  @Test
  @DisplayName("Flujo E4 feliz: valida producto en Donaciones, consulta stock y asigna en Logística")
  void flujoCompletoConStock() {
    String entidadId = crearEntidad();

    // 1) Donadores debe validar el producto contra Donaciones
    otrosModulos
        .expect(requestTo("http://localhost:8081/productos/7"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":\"7\",\"nombre\":\"Arroz\",\"descripcion\":\"Arroz largo fino\","
                    + "\"categoriaID\":\"alimentos\",\"identificadorID\":\"1\"}",
                MediaType.APPLICATION_JSON));

    // 2) Luego debe consultar stock a Logística
    otrosModulos
        .expect(requestTo("http://localhost:8082/stock/7"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"disponible\":3}", MediaType.APPLICATION_JSON));

    // 3) Como hay stock (3 < objetivo 5), debe solicitar asignar 3 con origen Donadores
    otrosModulos
        .expect(requestTo("http://localhost:8082/asignaciones/solicitud"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.productoID").value("7"))
        .andExpect(jsonPath("$.cantidad").value(3))
        .andExpect(jsonPath("$.origen").value("SOLICITUD_DONADORES"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    NecesidadMaterialDTO necesidad =
        new NecesidadMaterialDTO(
            null, entidadId, 8, "30 sillas tras inundacion", 5, "7",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    ResponseEntity<NecesidadMaterialDTO> resp =
        api.postForEntity("/necesidades", necesidad, NecesidadMaterialDTO.class);

    assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    assertNotNull(resp.getBody().id());

    // Se hicieron TODAS las llamadas esperadas, en orden
    otrosModulos.verify();

    // La necesidad quedó consultable por ID (endpoint nuevo de E4)
    ResponseEntity<NecesidadMaterialDTO> get =
        api.getForEntity("/necesidades/" + resp.getBody().id(), NecesidadMaterialDTO.class);
    assertEquals(HttpStatus.OK, get.getStatusCode());
  }

  @Test
  @DisplayName("Flujo E4: producto inexistente en Donaciones → necesidad rechazada con 400")
  void productoInexistenteRechazaNecesidad() {
    String entidadId = crearEntidad();

    otrosModulos
        .expect(requestTo("http://localhost:8081/productos/999"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    NecesidadMaterialDTO necesidad =
        new NecesidadMaterialDTO(
            null, entidadId, 5, "necesidad invalida", 5, "999",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    ResponseEntity<String> resp = api.postForEntity("/necesidades", necesidad, String.class);

    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    otrosModulos.verify(); // no hubo llamadas a Logística: se cortó en la validación
  }

  @Test
  @DisplayName("Flujo E4: sin stock en Logística → la necesidad se crea igual y NO se asigna")
  void sinStockNoAsigna() {
    String entidadId = crearEntidad();

    otrosModulos
        .expect(requestTo("http://localhost:8081/productos/7"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":\"7\",\"nombre\":\"Arroz\",\"descripcion\":\"Arroz largo fino\","
                    + "\"categoriaID\":\"alimentos\",\"identificadorID\":\"1\"}",
                MediaType.APPLICATION_JSON));
    otrosModulos
        .expect(requestTo("http://localhost:8082/stock/7"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"disponible\":0}", MediaType.APPLICATION_JSON));
    // Sin expectativa de POST /asignaciones/solicitud: si ocurriera, verify() fallaría

    NecesidadMaterialDTO necesidad =
        new NecesidadMaterialDTO(
            null, entidadId, 5, "sin stock disponible", 5, "7",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    ResponseEntity<NecesidadMaterialDTO> resp =
        api.postForEntity("/necesidades", necesidad, NecesidadMaterialDTO.class);

    assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    otrosModulos.verify();
  }
}
