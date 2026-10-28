package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import ar.edu.utn.dds.k3003.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.controllers.requests.CategoriaDonadorRequest;
import ar.edu.utn.dds.k3003.logging.TrazabilidadFilter;
import ar.edu.utn.dds.k3003.model.CambioCategoriaDonador;
import ar.edu.utn.dds.k3003.repositories.DonadoresRepository;
import ar.edu.utn.dds.k3003.repositories.EntidadesRepository;
import ar.edu.utn.dds.k3003.repositories.NecesidadesRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Los eventos con la app real: H2, la cadena de Spring entera y un RabbitTemplate simulado.
 *
 * <p>Cubre lo que los tests con repositorios en memoria no pueden: que el listener se dispare
 * después del commit (y no si se deshace), que el historial de categorías persista con JPA, el
 * endpoint de republicar y que el Swagger no duplique los de administración. La dirección de
 * RabbitMQ es de mentira: el RabbitTemplate está simulado y nada se conecta.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.rabbitmq.addresses=amqp://localhost:5672")
@ActiveProfiles("test")
class EventosIntegrationTest {

  @Autowired private TestRestTemplate api;
  @Autowired private EventosDonadores eventos;
  @Autowired private PublicadorEventos publicador;
  @Autowired private TransactionTemplate transaccion;
  @Autowired private MeterRegistry registro;
  // Por las interfaces propias: en la JPA, findById está declarado dos veces y es ambiguo.
  @Autowired private DonadoresRepository donadores;
  @Autowired private EntidadesRepository entidades;
  @Autowired private NecesidadesRepository necesidades;

  @MockitoBean private RabbitTemplate rabbit;

  private MensajesCapturados rabbitMQ;

  @BeforeEach
  void setUp() {
    // El mock se reinicia después de cada test: hay que volver a enchufarlo.
    rabbitMQ = MensajesCapturados.en(rabbit);
  }

  private DonadorDTO donador() {
    return new DonadorDTO(
        null, "Ana", "Pérez", 30, "ana@mail.com", "doc-" + System.nanoTime(), "Calle 1", null,
        null);
  }

  private String crearDonador() {
    return api.postForEntity("/donadores", donador(), DonadorDTO.class).getBody().id();
  }

  private double fallidos(String tipo) {
    return registro.find("eventos.fallidos").tag("tipo", tipo).counters().stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  @Test
  @DisplayName("Con RABBITMQ_URL el publicador queda habilitado")
  void habilitado() {
    assertTrue(publicador.estaHabilitado());
  }

  @Test
  @DisplayName("POST /donadores publica DONADOR_CREADO con el traceId del pedido")
  void altaPorHttp() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(TrazabilidadFilter.HEADER_TRACE, "traza-e6-001");

    ResponseEntity<DonadorDTO> resp =
        api.exchange(
            "/donadores", HttpMethod.POST, new HttpEntity<>(donador(), headers), DonadorDTO.class);

    assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    MensajesCapturados.Enviado enviado = rabbitMQ.unico();
    assertEquals("donatrack.eventos", enviado.exchange());
    assertEquals("donadores.donador.creado", enviado.routingKey());
    assertEquals(resp.getBody().id(), enviado.entidadId());
    assertEquals(
        "traza-e6-001",
        enviado.mensaje().getMessageProperties().getHeader(TrazabilidadFilter.HEADER_TRACE));
  }

  @Test
  @DisplayName("El evento emitido dentro de una transacción sale recién con el commit")
  void despuesDelCommit() {
    transaccion.executeWithoutResult(
        estado -> {
          eventos.emitir(eventos.necesidadEliminada("n-commit"));
          assertTrue(rabbitMQ.todos().isEmpty(), "salió antes del commit");
        });

    assertEquals("n-commit", rabbitMQ.unico().entidadId());
  }

  @Test
  @DisplayName("Si la transacción se deshace, el evento no se publica")
  void rollback() {
    transaccion.executeWithoutResult(
        estado -> {
          eventos.emitir(eventos.necesidadEliminada("n-rollback"));
          estado.setRollbackOnly();
        });

    assertTrue(rabbitMQ.todos().isEmpty(), rabbitMQ.tipos().toString());
  }

  @Test
  @DisplayName("El historial de categorías persiste en la base y se consulta por HTTP")
  void historialPersiste() {
    String id = crearDonador();
    rabbitMQ.limpiar();

    ResponseEntity<DonadorDTO> cambio =
        api.exchange(
            "/donadores/" + id + "/categoria",
            HttpMethod.PATCH,
            new HttpEntity<>(new CategoriaDonadorRequest("Oro")),
            DonadorDTO.class);
    assertEquals(HttpStatus.OK, cambio.getStatusCode());
    // Repetir la misma categoría no agrega nada ni publica.
    api.exchange(
        "/donadores/" + id + "/categoria",
        HttpMethod.PATCH,
        new HttpEntity<>(new CategoriaDonadorRequest("Oro")),
        DonadorDTO.class);

    // Leído de nuevo de la base, sin pasar por la fachada.
    List<String> guardado =
        donadores.findById(id).orElseThrow().getHistorialCategorias().stream()
            .map(CambioCategoriaDonador::getCategoria)
            .toList();
    assertEquals(List.of("Colaborador", "Oro"), guardado);

    ResponseEntity<JsonNode> resp =
        api.getForEntity("/donadores/" + id + "/categorias", JsonNode.class);
    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals(2, resp.getBody().size());
    assertEquals("Colaborador", resp.getBody().get(0).get("categoria").asText());
    assertEquals("Oro", resp.getBody().get(1).get("categoria").asText());
    assertTrue(resp.getBody().get(1).get("desde").asText().endsWith("Z"));

    assertEquals(List.of("DONADOR_MODIFICADO"), rabbitMQ.tipos());
    assertEquals(2, rabbitMQ.unico().datos().get("historialCategorias").size());
  }

  @Test
  @DisplayName("El historial de un donador inexistente responde 404")
  void historialInexistente() {
    assertEquals(
        HttpStatus.NOT_FOUND,
        api.getForEntity("/donadores/no-existe/categorias", String.class).getStatusCode());
  }

  @Test
  @DisplayName("Con RabbitMQ caído, el alta responde 201, queda guardada y el fallo se cuenta")
  void rabbitCaido() {
    doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class));
    double antes = fallidos("DONADOR_CREADO");

    ResponseEntity<DonadorDTO> resp = api.postForEntity("/donadores", donador(), DonadorDTO.class);

    assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    assertEquals(
        HttpStatus.OK,
        api.getForEntity("/donadores/" + resp.getBody().id(), String.class).getStatusCode());
    assertEquals(1, fallidos("DONADOR_CREADO") - antes);
  }

  @Test
  @DisplayName("Con RabbitMQ caído la salud del módulo sigue en UP")
  void saludSinBroker() {
    // La dirección apunta a un broker que no existe: si el chequeo de RabbitMQ estuviera activo,
    // todo el módulo figuraría caído por algo que no le impide funcionar.
    ResponseEntity<JsonNode> resp = api.getForEntity("/actuator/health", JsonNode.class);

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals("UP", resp.getBody().get("status").asText());
  }

  @Test
  @DisplayName("POST /admin/republicar-eventos publica todo el estado actual y dice cuántos")
  void republicar() {
    String donadorID = crearDonador();
    api.postForEntity(
        "/donadores/" + donadorID + "/quejas",
        new QuejaDTO(null, "donacion1", null, null, "llegó roto"),
        String.class);
    api.postForEntity(
        "/entidades",
        new EntidadBeneficaDTO(null, "Comedor", "Calle 1", "11", "c@mail.com"),
        String.class);
    rabbitMQ.limpiar();
    int quejas =
        donadores.findAll().stream().mapToInt(d -> d.getQuejas().size()).sum();
    int esperados =
        donadores.findAll().size() + entidades.findAll().size() + necesidades.findAll().size()
            + quejas;

    ResponseEntity<JsonNode> resp =
        api.postForEntity("/admin/republicar-eventos", null, JsonNode.class);

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals(esperados, resp.getBody().get("publicados").asInt());
    assertEquals(0, resp.getBody().get("fallidos").asInt());
    assertEquals(esperados, rabbitMQ.todos().size());
    assertTrue(rabbitMQ.tipos().contains("QUEJA_REGISTRADA"));
    assertTrue(
        rabbitMQ.tipos().stream()
            .allMatch(
                t -> List.of(
                        "DONADOR_MODIFICADO", "ENTIDAD_MODIFICADA", "NECESIDAD_MODIFICADA",
                        "QUEJA_REGISTRADA")
                    .contains(t)),
        rabbitMQ.tipos().toString());
  }

  @Test
  @DisplayName("DELETE /reset publica MODULO_RESETEADO cuando se confirma el borrado")
  void reset() {
    crearDonador();
    rabbitMQ.limpiar();

    assertEquals(
        HttpStatus.OK,
        api.exchange("/reset", HttpMethod.DELETE, null, String.class).getStatusCode());

    MensajesCapturados.Enviado enviado = rabbitMQ.unico();
    assertEquals("donadores.modulo.reseteado", enviado.routingKey());
    assertEquals("*", enviado.entidadId());
    assertTrue(donadores.findAll().isEmpty());
  }

  @Test
  @DisplayName("En Swagger, los endpoints de administración aparecen una sola vez y en su grupo")
  void swaggerSinDuplicados() throws Exception {
    JsonNode docs =
        new ObjectMapper().readTree(api.getForObject("/v3/api-docs", String.class));
    JsonNode paths = docs.get("paths");

    assertEquals(
        List.of("Administración"), etiquetas(paths.get("/admin/republicar-eventos").get("post")));
    assertEquals(List.of("Administración"), etiquetas(paths.get("/reset").get("delete")));
    assertEquals(
        List.of("Consultas"), etiquetas(paths.get("/donadores/{id}/categorias").get("get")));
  }

  private static List<String> etiquetas(JsonNode operacion) {
    return java.util.stream.StreamSupport.stream(operacion.get("tags").spliterator(), false)
        .map(JsonNode::asText)
        .toList();
  }
}
