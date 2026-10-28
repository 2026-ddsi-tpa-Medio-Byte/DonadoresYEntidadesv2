package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.controllers.responses.CambioCategoriaResponse;
import java.util.List;

/**
 * Lo que va en {@code datos} de cada evento, campo por campo como dice el contrato.
 *
 * <p>Son records propios y no los DTO de cátedra para que publicar no exponga más de lo acordado:
 * {@code DonadorDTO} trae email, documento y domicilio, y ninguna consulta los usa. Un dato
 * personal que no sale del módulo no se puede filtrar en el modelo de lectura. Las fechas ya van
 * como texto en UTC con {@code Z} (ver {@link FechasUtc}).
 */
public final class DatosEvento {

  private DatosEvento() {}

  /** {@code DONADOR_CREADO} y {@code DONADOR_MODIFICADO}. */
  public record DeDonador(
      String id,
      String nombre,
      String apellido,
      EstadoDonadorEnum estado,
      String categoria,
      List<CambioCategoriaResponse> historialCategorias,
      Integer cantidadQuejas) {}

  /** {@code QUEJA_REGISTRADA}. */
  public record DeQueja(
      String id, String donacionID, String donadorID, String fecha, String descripcion) {}

  /** {@code ENTIDAD_CREADA} y {@code ENTIDAD_MODIFICADA}. Sin domicilio, teléfono ni correo. */
  public record DeEntidad(String id, String razonSocial) {}

  /** {@code NECESIDAD_REGISTRADA} y {@code NECESIDAD_MODIFICADA}. */
  public record DeNecesidad(
      String id,
      String entidadID,
      String productoSolicitadoID,
      Integer nivelDeUrgencia,
      TipoNecesidadMaterialEnum tipo,
      Integer cantidadObjetivo,
      Integer cantidadCubierta,
      String descripcion) {}

  /** {@code NECESIDAD_ELIMINADA}: alcanza con el id para que Consultas borre el documento. */
  public record SoloId(String id) {}
}
