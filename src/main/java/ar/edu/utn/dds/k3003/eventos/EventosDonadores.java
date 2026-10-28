package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.model.Donador;
import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.model.Queja;
import ar.edu.utn.dds.k3003.repositories.DonadoresYEntidadesDataMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * El catálogo de eventos del módulo: cómo se arma cada uno a partir del objeto que cambió.
 *
 * <p>Armar y emitir están separados a propósito. El evento se arma en el momento del cambio, con el
 * estado de ese instante; emitirlo lo entrega como evento de aplicación de Spring, y
 * {@link PublicadorEventos} lo manda a RabbitMQ recién después del commit. La fachada no sabe nada
 * de RabbitMQ.
 */
@Component
public class EventosDonadores {

  public static final String MODULO = "donadores";

  /** Lo que lleva {@code entidadId} en {@code MODULO_RESETEADO}: todo el módulo. */
  public static final String TODO_EL_MODULO = "*";

  private final ApplicationEventPublisher publicadorDeAplicacion;
  private final Clock reloj;
  private final DonadoresYEntidadesDataMapper mapper = new DonadoresYEntidadesDataMapper();

  /** El último {@code ocurridoEn} entregado, en milisegundos. */
  private final AtomicLong ultimoInstante = new AtomicLong();

  @Autowired
  public EventosDonadores(ApplicationEventPublisher publicadorDeAplicacion) {
    this(publicadorDeAplicacion, Clock.systemUTC());
  }

  /** Con un reloj propio, para poder fijar la hora en los tests. */
  public EventosDonadores(ApplicationEventPublisher publicadorDeAplicacion, Clock reloj) {
    this.publicadorDeAplicacion = publicadorDeAplicacion;
    this.reloj = reloj;
  }

  /** Lo entrega a Spring. Si hay una transacción en curso, sale a RabbitMQ cuando se confirme. */
  public void emitir(EventoDonadores evento) {
    publicadorDeAplicacion.publishEvent(evento);
  }

  // ── Donadores ───────────────────────────────────────────────────────────────

  public EventoDonadores donadorCreado(Donador donador) {
    return armar(TipoEvento.DONADOR_CREADO, donador.getId(), datosDe(donador));
  }

  /** Cambio de estado (a mano o por quejas), de categoría o de la cantidad de quejas. */
  public EventoDonadores donadorModificado(Donador donador) {
    return armar(TipoEvento.DONADOR_MODIFICADO, donador.getId(), datosDe(donador));
  }

  public EventoDonadores quejaRegistrada(Queja queja) {
    return armar(
        TipoEvento.QUEJA_REGISTRADA,
        queja.getId(),
        new DatosEvento.DeQueja(
            queja.getId(),
            queja.getDonacionID(),
            queja.getDonadorID(),
            FechasUtc.inicioDelDia(queja.getFecha()),
            queja.getDescripcion()));
  }

  // ── Entidades ───────────────────────────────────────────────────────────────

  public EventoDonadores entidadCreada(EntidadBenefica entidad) {
    return armar(TipoEvento.ENTIDAD_CREADA, entidad.getId(), datosDe(entidad));
  }

  public EventoDonadores entidadModificada(EntidadBenefica entidad) {
    return armar(TipoEvento.ENTIDAD_MODIFICADA, entidad.getId(), datosDe(entidad));
  }

  // ── Necesidades ─────────────────────────────────────────────────────────────

  public EventoDonadores necesidadRegistrada(NecesidadMaterial necesidad) {
    return armar(TipoEvento.NECESIDAD_REGISTRADA, necesidad.getId(), datosDe(necesidad));
  }

  /** Edición, o satisfacción por una entrega: lleva la {@code cantidadCubierta} resultante. */
  public EventoDonadores necesidadModificada(NecesidadMaterial necesidad) {
    return armar(TipoEvento.NECESIDAD_MODIFICADA, necesidad.getId(), datosDe(necesidad));
  }

  public EventoDonadores necesidadEliminada(String necesidadID) {
    return armar(TipoEvento.NECESIDAD_ELIMINADA, necesidadID, new DatosEvento.SoloId(necesidadID));
  }

  // ── Módulo ──────────────────────────────────────────────────────────────────

  /** Consultas borra todo lo de Donadores anterior a este evento. */
  public EventoDonadores moduloReseteado() {
    return armar(TipoEvento.MODULO_RESETEADO, TODO_EL_MODULO, Map.of());
  }

  // ── Armado ──────────────────────────────────────────────────────────────────

  private EventoDonadores armar(TipoEvento tipo, String entidadId, Object datos) {
    SobreEvento sobre =
        new SobreEvento(
            UUID.randomUUID().toString(),
            tipo.name(),
            MODULO,
            entidadId,
            FechasUtc.formatear(ahora()),
            datos);
    return new EventoDonadores(tipo.routingKey(), sobre);
  }

  /**
   * La hora del evento, en milisegundos y estrictamente creciente dentro del módulo.
   *
   * <p>Consultas solo pisa un documento con un evento más nuevo. Con la hora a secas, dos cambios
   * del mismo objeto en el mismo milisegundo (dos entregas simultáneas de una necesidad) empatan, y
   * si el más viejo llega primero el más nuevo se descarta: el modelo de lectura queda atrasado.
   * Si el reloj no avanzó desde el último evento, se le suma un milisegundo. Junto con armar el
   * evento dentro del candado de {@code satisfacerNecesidad}, garantiza que la entrega que se
   * registró última es también la que lleva la hora más nueva.
   */
  private Instant ahora() {
    long reloj = this.reloj.millis();
    return Instant.ofEpochMilli(ultimoInstante.updateAndGet(previo -> Math.max(reloj, previo + 1)));
  }

  private DatosEvento.DeDonador datosDe(Donador donador) {
    return new DatosEvento.DeDonador(
        donador.getId(),
        donador.getNombre(),
        donador.getApellido(),
        donador.getEstado(),
        donador.getCategoria(),
        mapper.toHistorialCategorias(donador),
        donador.getQuejas() == null ? 0 : donador.cantidadQuejas());
  }

  private static DatosEvento.DeEntidad datosDe(EntidadBenefica entidad) {
    return new DatosEvento.DeEntidad(entidad.getId(), entidad.getRazonSocial());
  }

  private static DatosEvento.DeNecesidad datosDe(NecesidadMaterial necesidad) {
    return new DatosEvento.DeNecesidad(
        necesidad.getId(),
        necesidad.getEntidadID(),
        necesidad.getProductoSolicitadoID(),
        necesidad.getNivelDeUrgencia(),
        necesidad.getTipo(),
        necesidad.getCantidadObjetivo(),
        necesidad.getCantidadCubierta() != null ? necesidad.getCantidadCubierta() : 0,
        necesidad.getDescripcion());
  }
}
