package ar.edu.utn.dds.k3003.controllers.responses;

/**
 * Una entrada del historial de categorías: qué categoría y desde cuándo, en UTC con {@code Z}.
 *
 * <p>Es la misma forma que viaja en {@code historialCategorias} de los eventos del donador, a
 * propósito: el endpoint de consulta y el modelo de lectura de Consultas no pueden contar dos
 * historiales distintos.
 */
public record CambioCategoriaResponse(String categoria, String desde) {}
