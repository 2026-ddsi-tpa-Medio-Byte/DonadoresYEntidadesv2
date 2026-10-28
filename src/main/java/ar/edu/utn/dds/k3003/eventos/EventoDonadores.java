package ar.edu.utn.dds.k3003.eventos;

/**
 * Un evento listo para publicar: a dónde va y qué lleva.
 *
 * <p>Es también el evento de aplicación de Spring que escucha {@link PublicadorEventos}. Se arma en
 * el momento del cambio, con el estado de ese instante, aunque se publique más tarde (después del
 * commit, o fuera del candado de {@code satisfacerNecesidad}).
 */
public record EventoDonadores(String routingKey, SobreEvento sobre) {}
