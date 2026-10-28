package ar.edu.utn.dds.k3003.eventos;

/**
 * El cuerpo JSON de cada mensaje: el sobre común a los cuatro módulos.
 *
 * <p>{@code ocurridoEn} es texto y no {@code Instant} para fijar el formato del contrato (UTC, con
 * milisegundos y {@code Z}) sin depender de cómo esté configurado Jackson. {@code datos} lleva el
 * estado completo del objeto después del cambio, nunca la diferencia.
 */
public record SobreEvento(
    String eventoId,
    String tipo,
    String modulo,
    String entidadId,
    String ocurridoEn,
    Object datos) {}
