package ar.edu.utn.dds.k3003.eventos;

/**
 * Cómo salió un {@code POST /admin/republicar-eventos}.
 *
 * <p>Se informan los fallidos además de los publicados: una carga inicial a medias se tiene que
 * notar en la respuesta, no solo en el log.
 */
public record ResultadoRepublicacion(int publicados, int fallidos) {}
