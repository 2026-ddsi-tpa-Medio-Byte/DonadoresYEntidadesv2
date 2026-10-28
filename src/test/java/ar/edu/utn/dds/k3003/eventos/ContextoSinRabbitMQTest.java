package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.utn.dds.k3003.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sin {@code RABBITMQ_URL}, como en local y en los tests: el contexto arranca, el módulo anda y
 * nadie intenta conectarse a RabbitMQ.
 *
 * <p>Usa la configuración de los otros tests de integración a propósito: es la que corre cuando
 * falta la variable, y así además comparte el contexto ya levantado.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContextoSinRabbitMQTest {

  @Autowired private TestRestTemplate api;
  @Autowired private PublicadorEventos publicador;
  @Autowired private CachingConnectionFactory conexiones;

  @Test
  @DisplayName("Sin RABBITMQ_URL arranca, opera normalmente y no abre ni intenta una conexión")
  void arrancaSinBroker() {
    List<String> intentos = new ArrayList<>();
    // Si ya hubiera una conexión abierta, se avisa en el momento de registrarse.
    conexiones.addConnectionListener(
        new ConnectionListener() {
          @Override
          public void onCreate(Connection conexion) {
            intentos.add("abrió una conexión");
          }

          @Override
          public void onFailed(Exception e) {
            intentos.add("intentó conectar: " + e.getMessage());
          }
        });

    assertFalse(publicador.estaHabilitado());
    ResponseEntity<DonadorDTO> alta =
        api.postForEntity(
            "/donadores",
            new DonadorDTO(
                null, "Ana", "Pérez", 30, "a@mail.com", "doc-" + System.nanoTime(), "Calle 1",
                null, null),
            DonadorDTO.class);
    assertEquals(HttpStatus.CREATED, alta.getStatusCode());
    assertEquals(
        HttpStatus.CREATED,
        api.postForEntity(
                "/donadores/" + alta.getBody().id() + "/quejas",
                new QuejaDTO(null, "donacion1", null, null, "llegó roto"),
                String.class)
            .getStatusCode());
    assertEquals(
        HttpStatus.OK,
        api.exchange("/donadores/" + alta.getBody().id(), HttpMethod.GET, null, String.class)
            .getStatusCode());

    ResponseEntity<JsonNode> salud = api.getForEntity("/actuator/health", JsonNode.class);
    assertEquals("UP", salud.getBody().get("status").asText());
    assertFalse(salud.getBody().path("components").has("rabbit"));

    assertTrue(intentos.isEmpty(), intentos.toString());
  }

  @Test
  @DisplayName("Republicar sin RABBITMQ_URL responde 503 y dice por qué, en vez de 0 publicados")
  void republicarDeshabilitado() {
    ResponseEntity<String> resp =
        api.postForEntity("/admin/republicar-eventos", null, String.class);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resp.getStatusCode());
    assertTrue(resp.getBody().contains("RABBITMQ_URL"), resp.getBody());
  }
}
