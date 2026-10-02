package ar.edu.utn.dds.k3003.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * El cliente de Logística tiene que distinguir «no hay stock» de «Logística no contestó», y
 * «asignación confirmada» de «no se pudo asignar». Con esas dos respuestas decide la fachada si
 * marca la necesidad como cubierta.
 */
class LogisticaStockClientTest {

  private static final String URL = "http://logistica.test";

  private MockRestServiceServer logistica;
  private LogisticaStockClient cliente;

  @BeforeEach
  void setUp() {
    // Con los conversores de Spring Boot, como en producción: un RestTemplate pelado pone el de
    // XML primero (jackson-dataformat-xml está en el classpath) y mandaría el cuerpo en XML.
    RestTemplate restTemplate = new RestTemplate(new HttpMessageConverters().getConverters());
    logistica = MockRestServiceServer.bindTo(restTemplate).build();
    cliente = new LogisticaStockClient(restTemplate);
    ReflectionTestUtils.setField(cliente, "logisticaUrl", URL);
  }

  @Test
  @DisplayName("Con stock informado devuelve la cantidad disponible")
  void conStock() {
    logistica
        .expect(requestTo(URL + "/stock/7"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"disponible\":3}", MediaType.APPLICATION_JSON));

    assertEquals(OptionalInt.of(3), cliente.consultarStockDisponible("7"));
  }

  @Test
  @DisplayName("Sin stock, Logística contesta 0: es una respuesta, no una falla")
  void sinStock() {
    logistica
        .expect(requestTo(URL + "/stock/7"))
        .andRespond(withSuccess("{\"disponible\":0}", MediaType.APPLICATION_JSON));

    assertEquals(OptionalInt.of(0), cliente.consultarStockDisponible("7"));
  }

  @Test
  @DisplayName("Si Logística falla, no contesta o rompe el contrato, el stock queda desconocido")
  void stockDesconocido() {
    logistica.expect(requestTo(URL + "/stock/7")).andRespond(withServerError());
    logistica
        .expect(requestTo(URL + "/stock/7"))
        .andRespond(
            pedido -> {
              throw new ConnectException("Connection refused");
            });
    // Logística contesta 200 con {"disponible": 0} para un producto sin stock: un 404 o una
    // respuesta sin el campo quiere decir que la ruta o el contrato cambiaron.
    logistica.expect(requestTo(URL + "/stock/7")).andRespond(withStatus(HttpStatus.NOT_FOUND));
    logistica
        .expect(requestTo(URL + "/stock/7"))
        .andRespond(withSuccess("{\"stock\":5}", MediaType.APPLICATION_JSON));

    for (int i = 0; i < 4; i++) {
      assertEquals(OptionalInt.empty(), cliente.consultarStockDisponible("7"), "caso " + i);
    }
    logistica.verify();
  }

  @Test
  @DisplayName("Una asignación que Logística acepta se informa como confirmada")
  void asignacionConfirmada() {
    logistica
        .expect(requestTo(URL + "/asignaciones/solicitud"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.necesidadID").value("n1"))
        .andExpect(jsonPath("$.cantidad").value(4))
        .andExpect(jsonPath("$.origen").value("SOLICITUD_DONADORES"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertTrue(cliente.solicitarAsignacion("n1", "7", 4));
  }

  @Test
  @DisplayName("Cualquier 2xx confirma la asignación, traiga el cuerpo que traiga")
  void confirmacionSoloPorElCodigo() {
    // El formato de la respuesta de Logística no está documentado. Si la confirmación dependiera
    // de poder leer el cuerpo, un "OK" en texto plano dejaría la necesidad en 0 con las unidades
    // ya apartadas del otro lado.
    logistica
        .expect(requestTo(URL + "/asignaciones/solicitud"))
        .andRespond(withSuccess("Asignación creada", MediaType.TEXT_PLAIN));
    logistica
        .expect(requestTo(URL + "/asignaciones/solicitud"))
        .andRespond(withStatus(HttpStatus.CREATED));

    assertTrue(cliente.solicitarAsignacion("n1", "7", 4));
    assertTrue(cliente.solicitarAsignacion("n1", "7", 4));
    logistica.verify();
  }

  @Test
  @DisplayName("Si Logística rechaza la asignación o no contesta, no se da por confirmada")
  void asignacionNoConfirmada() {
    logistica.expect(requestTo(URL + "/asignaciones/solicitud")).andRespond(withBadRequest());
    logistica.expect(requestTo(URL + "/asignaciones/solicitud")).andRespond(withServerError());
    logistica
        .expect(requestTo(URL + "/asignaciones/solicitud"))
        .andRespond(
            pedido -> {
              throw new ConnectException("Connection refused");
            });

    assertFalse(cliente.solicitarAsignacion("n1", "7", 4));
    assertFalse(cliente.solicitarAsignacion("n1", "7", 4));
    assertFalse(cliente.solicitarAsignacion("n1", "7", 4));
    logistica.verify();
  }
}
