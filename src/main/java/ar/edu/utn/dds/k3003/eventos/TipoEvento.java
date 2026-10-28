package ar.edu.utn.dds.k3003.eventos;

/**
 * Los eventos que publica Donadores y Entidades, cada uno con su routing key.
 *
 * <p>Nombres y claves salen de {@code guias-entrega-6/CONTRATO-EVENTOS.md}. Consultas decide por el
 * campo {@code tipo}, que es el nombre de la constante; la routing key sigue el formato
 * {@code <modulo>.<objeto>.<acción>} que adoptaron los cuatro módulos. Van juntos para que un tipo
 * nuevo no pueda salir sin su clave.
 */
public enum TipoEvento {
  DONADOR_CREADO("donadores.donador.creado"),
  DONADOR_MODIFICADO("donadores.donador.modificado"),
  QUEJA_REGISTRADA("donadores.queja.registrada"),
  ENTIDAD_CREADA("donadores.entidad.creada"),
  ENTIDAD_MODIFICADA("donadores.entidad.modificada"),
  NECESIDAD_REGISTRADA("donadores.necesidad.registrada"),
  NECESIDAD_MODIFICADA("donadores.necesidad.modificada"),
  NECESIDAD_ELIMINADA("donadores.necesidad.eliminada"),
  MODULO_RESETEADO("donadores.modulo.reseteado");

  private final String routingKey;

  TipoEvento(String routingKey) {
    this.routingKey = routingKey;
  }

  public String routingKey() {
    return routingKey;
  }
}
