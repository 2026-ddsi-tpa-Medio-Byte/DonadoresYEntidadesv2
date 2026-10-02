package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Los códigos HTTP con los que el módulo responde a pedidos inválidos o a recursos inexistentes.
 *
 * <p>Va contra la app real con H2 para cubrir también los repositorios JPA: el chequeo de documento
 * repetido y la baja de necesidades viven ahí, y los tests con repositorios en memoria no los
 * tocan. Ninguno de estos pedidos sale a otro módulo, así que no hace falta simularlos.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CodigosHttpIntegrationTest {

  @Autowired private TestRestTemplate api;

  /** La base se comparte con los otros tests de integración: cada documento tiene que ser único. */
  private String documentoUnico() {
    return "doc-" + System.nanoTime();
  }

  private DonadorDTO donador(String nroDocumento) {
    return new DonadorDTO(
        null, "Ana", "Pérez", 30, "ana@mail.com", nroDocumento, "Calle 1", null, null);
  }

  @Test
  @DisplayName("Dar de alta un donador con un documento ya registrado responde 409")
  void documentoRepetidoEs409() {
    String documento = documentoUnico();
    ResponseEntity<String> primero = api.postForEntity("/donadores", donador(documento), String.class);
    assertEquals(HttpStatus.CREATED, primero.getStatusCode());

    ResponseEntity<String> repetido =
        api.postForEntity("/donadores", donador(documento), String.class);

    assertEquals(HttpStatus.CONFLICT, repetido.getStatusCode());
    assertEquals("Ya existe un donador con el documento " + documento, repetido.getBody());
  }

  @Test
  @DisplayName("Dar de alta un donador con campos en blanco responde 400 y nombra los que faltan")
  void camposEnBlancoEs400() {
    DonadorDTO incompleto =
        new DonadorDTO(null, "Ana", " ", 30, "", documentoUnico(), "Calle 1", null, null);

    ResponseEntity<String> resp = api.postForEntity("/donadores", incompleto, String.class);

    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    assertEquals("Faltan datos obligatorios del donador: apellido, email", resp.getBody());
  }

  @Test
  @DisplayName("Borrar una necesidad que no existe responde 404")
  void borrarNecesidadInexistenteEs404() {
    ResponseEntity<String> resp =
        api.exchange("/necesidades/no-existe", HttpMethod.DELETE, null, String.class);

    assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
  }

  @Test
  @DisplayName("Una queja sin descripción responde 400")
  void quejaSinDescripcionEs400() {
    String donadorID =
        api.postForEntity("/donadores", donador(documentoUnico()), DonadorDTO.class).getBody().id();

    ResponseEntity<String> resp =
        api.postForEntity(
            "/donadores/" + donadorID + "/quejas",
            new QuejaDTO(null, "donacion1", null, null, "  "),
            String.class);

    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    assertEquals("La queja tiene que tener una descripción", resp.getBody());
  }

  @Test
  @DisplayName("Modificar una necesidad con objetivo 0 responde 400")
  void objetivoCeroEs400() {
    String entidadID =
        api.postForEntity(
                "/entidades",
                new EntidadBeneficaDTO(null, "Comedor", "Calle 1", "11", "c@mail.com"),
                EntidadBeneficaDTO.class)
            .getBody()
            .id();
    // Sin producto, para que el alta no salga a Donaciones ni a Logística.
    String necesidadID =
        api.postForEntity(
                "/necesidades",
                new NecesidadMaterialDTO(
                    null, entidadID, 5, "frazadas", 10, null,
                    TipoNecesidadMaterialEnum.EXTRAORDINARIA),
                NecesidadMaterialDTO.class)
            .getBody()
            .id();

    ResponseEntity<String> resp =
        api.exchange(
            "/necesidades/" + necesidadID,
            HttpMethod.PUT,
            new HttpEntity<>(new NecesidadMaterialDTO(null, null, null, null, 0, null, null)),
            String.class);

    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
  }
}
