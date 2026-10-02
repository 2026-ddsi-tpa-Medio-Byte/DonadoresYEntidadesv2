package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.clients.LogisticaStockClient;
import ar.edu.utn.dds.k3003.repositories.InMemoryDonadoresRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryEntidadesRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryNecesidadesRepo;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Al registrar una necesidad se le pide a Logística el stock que haya y se marca como cubierto.
 *
 * <p>La satisfacción local se mantiene (es lo que se mostró en la Prueba 6 de la Entrega 4), pero
 * solo si Logística confirmó la asignación, y una RECURRENTE solo se asigna si el stock cubre todo
 * lo que falta. Si no, los dos módulos quedarían contando cosas distintas.
 */
@ExtendWith(MockitoExtension.class)
class StockAlRegistrarNecesidadTest {

  private static final String PRODUCTO = "7";

  @Mock private LogisticaStockClient logistica;

  private SimpleMeterRegistry registro;
  private Fachada fachada;
  private String entidadID;

  private Logger logger;
  private ListAppender<ILoggingEvent> eventos;

  @BeforeEach
  void setUp() {
    registro = new SimpleMeterRegistry();
    fachada =
        new Fachada(
            new InMemoryDonadoresRepo(),
            new InMemoryEntidadesRepo(),
            new InMemoryNecesidadesRepo(),
            new MetricasService(registro));
    entidadID =
        fachada
            .agregarEntidad(new EntidadBeneficaDTO(null, "Comedor", "Calle 1", "11", "c@mail.com"))
            .id();
    // Se enchufa después del alta de la entidad: Donaciones no está, así que el producto no se
    // valida y el test mira solo la parte de Logística.
    fachada.setLogisticaStockClient(logistica);

    logger = (Logger) LoggerFactory.getLogger(Fachada.class);
    eventos = new ListAppender<>();
    eventos.start();
    logger.addAppender(eventos);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(eventos);
  }

  private String registrar(int objetivo, TipoNecesidadMaterialEnum tipo) {
    return fachada
        .registrarNecesidad(
            new NecesidadMaterialDTO(null, entidadID, 5, "frazadas", objetivo, PRODUCTO, tipo))
        .id();
  }

  private int cubierta(String necesidadID) {
    return fachada.buscarNecesidadPorID(necesidadID).cantidadActual();
  }

  private double llamadas(String accion, String resultado) {
    return registro.find("integracion.llamadas")
        .tag("destino", "logistica")
        .tag("accion", accion)
        .tag("resultado", resultado)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private List<String> mensajes(Level nivel) {
    return eventos.list.stream()
        .filter(e -> e.getLevel() == nivel)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  @Test
  @DisplayName("Extraordinaria con stock suficiente: se asigna todo y queda cubierta (Prueba 6)")
  void extraordinariaConStockSuficiente() {
    when(logistica.consultarStockDisponible(PRODUCTO)).thenReturn(OptionalInt.of(10));
    when(logistica.solicitarAsignacion(anyString(), anyString(), anyInt())).thenReturn(true);

    String id = registrar(6, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    verify(logistica).solicitarAsignacion(id, PRODUCTO, 6);
    assertEquals(6, cubierta(id));
    assertEquals(1, llamadas("consultar_stock", "ok"));
    assertEquals(1, llamadas("solicitar_asignacion", "ok"));
  }

  @Test
  @DisplayName("Extraordinaria con stock parcial: se asigna y se cubre lo que hay")
  void extraordinariaConStockParcial() {
    when(logistica.consultarStockDisponible(PRODUCTO)).thenReturn(OptionalInt.of(4));
    when(logistica.solicitarAsignacion(anyString(), anyString(), anyInt())).thenReturn(true);

    String id = registrar(10, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    verify(logistica).solicitarAsignacion(id, PRODUCTO, 4);
    assertEquals(4, cubierta(id));
  }

  @Test
  @DisplayName("Recurrente con stock parcial: no se pide asignación y queda en 0")
  void recurrenteConStockParcial() {
    when(logistica.consultarStockDisponible(PRODUCTO)).thenReturn(OptionalInt.of(4));

    String id = registrar(10, TipoNecesidadMaterialEnum.RECURRENTE);

    // Si se pidiera, Logística apartaría 4 unidades que Donadores no puede registrar.
    verify(logistica, never()).solicitarAsignacion(anyString(), anyString(), anyInt());
    assertEquals(0, cubierta(id));
    assertTrue(
        mensajes(Level.INFO).stream().anyMatch(m -> m.contains("RECURRENTE")),
        mensajes(Level.INFO).toString());
  }

  @Test
  @DisplayName("Recurrente con stock suficiente: se asigna todo y queda cubierta")
  void recurrenteConStockSuficiente() {
    when(logistica.consultarStockDisponible(PRODUCTO)).thenReturn(OptionalInt.of(15));
    when(logistica.solicitarAsignacion(anyString(), anyString(), anyInt())).thenReturn(true);

    String id = registrar(10, TipoNecesidadMaterialEnum.RECURRENTE);

    verify(logistica).solicitarAsignacion(id, PRODUCTO, 10);
    assertEquals(10, cubierta(id));
  }

  @Test
  @DisplayName("Si Logística no confirma la asignación, la necesidad se crea igual en 0")
  void fallaAlAsignar() {
    when(logistica.consultarStockDisponible(PRODUCTO)).thenReturn(OptionalInt.of(10));
    when(logistica.solicitarAsignacion(anyString(), anyString(), anyInt())).thenReturn(false);

    String id = registrar(6, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    assertEquals(0, cubierta(id));
    assertEquals(1, llamadas("solicitar_asignacion", "error"));
    assertEquals(0, llamadas("solicitar_asignacion", "ok"));
    assertTrue(
        mensajes(Level.WARN).stream().anyMatch(m -> m.contains(id)),
        mensajes(Level.WARN).toString());
  }

  @Test
  @DisplayName("Si Logística no informa el stock, la necesidad se crea igual en 0 y sin pedir nada")
  void fallaAlConsultarStock() {
    when(logistica.consultarStockDisponible(PRODUCTO)).thenReturn(OptionalInt.empty());

    String id = registrar(6, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    verify(logistica, never()).solicitarAsignacion(anyString(), anyString(), anyInt());
    assertEquals(0, cubierta(id));
    assertEquals(1, llamadas("consultar_stock", "error"));
    assertEquals(0, llamadas("consultar_stock", "ok"));
    assertTrue(
        mensajes(Level.WARN).stream().anyMatch(m -> m.contains(id)),
        mensajes(Level.WARN).toString());
  }

  @Test
  @DisplayName("Sin stock, Logística contestó bien: la consulta cuenta como exitosa y no se pide nada")
  void sinStock() {
    when(logistica.consultarStockDisponible(PRODUCTO)).thenReturn(OptionalInt.of(0));

    String id = registrar(6, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    verify(logistica, never()).solicitarAsignacion(anyString(), anyString(), anyInt());
    assertEquals(0, cubierta(id));
    assertEquals(1, llamadas("consultar_stock", "ok"));
  }
}
