package ar.edu.utn.dds.k3003.eventos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * El único formato de fecha que sale del módulo hacia los eventos y el historial de categorías.
 *
 * <p>El contrato de eventos exige UTC con {@code Z}: sin zona, Consultas no puede comparar fechas
 * de módulos distintos. Se escribe siempre con milisegundos y ancho fijo
 * ({@code 2026-10-26T18:32:10.123Z}): Jackson omite la fracción cuando es cero, y con ancho fijo dos
 * fechas también se pueden comparar como texto sin equivocarse.
 */
public final class FechasUtc {

  /**
   * Dónde opera DonaTrack. Solo se usa para ubicar en el tiempo las fechas que no traen hora.
   *
   * <p>La fecha de una queja es un día, no un instante. Se publica como el comienzo de ese día en
   * Argentina, expresado en UTC: el 26/10 sale como {@code 2026-10-26T03:00:00.000Z}. Así el día no
   * cambia ni leyéndola en UTC ni en hora argentina. Con medianoche UTC ({@code T00:00Z}), quien la
   * mostrara en hora argentina vería el día anterior, a las 21.
   */
  public static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Argentina/Buenos_Aires");

  private static final DateTimeFormatter FORMATO =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private FechasUtc() {}

  /** Un instante en UTC con milisegundos y {@code Z}. Null si no hay fecha. */
  public static String formatear(Instant instante) {
    return instante == null ? null : FORMATO.format(instante);
  }

  /** Una fecha sin hora, como el comienzo de ese día en {@link #ZONA_NEGOCIO}, en UTC. */
  public static String inicioDelDia(LocalDate fecha) {
    return fecha == null ? null : formatear(fecha.atStartOfDay(ZONA_NEGOCIO).toInstant());
  }
}
