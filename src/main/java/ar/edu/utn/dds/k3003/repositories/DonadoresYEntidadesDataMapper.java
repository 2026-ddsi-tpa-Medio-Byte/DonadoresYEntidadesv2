package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.controllers.responses.CambioCategoriaResponse;
import ar.edu.utn.dds.k3003.controllers.responses.NecesidadResponse;
import ar.edu.utn.dds.k3003.eventos.FechasUtc;
import ar.edu.utn.dds.k3003.model.Donador;
import java.util.List;
import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.model.Queja;

public class DonadoresYEntidadesDataMapper {

  public DonadorDTO toDonadorDTO(Donador donador) {
    return new DonadorDTO(
            donador.getId(),
            donador.getNombre(),
            donador.getApellido(),
            donador.getEdad(),
            donador.getEmail(),
            donador.getNroDocumento(),
            donador.getDomicilio(),
            donador.getEstado(),
            donador.getCategoria());
  }

  public Donador toDonador(DonadorDTO dto) {

    EstadoDonadorEnum estado =
            dto.estado() != null ? dto.estado() : EstadoDonadorEnum.VERIFICADO;

    String categoria =
            dto.categoria() != null ? dto.categoria() : Donador.CATEGORIA_INICIAL;

    // La categoría va en el constructor para que sea la primera del historial.
    Donador donador = new Donador(
            dto.id(),
            dto.nombre(),
            dto.apellido(),
            dto.edad(),
            dto.email(),
            dto.nroDocumento(),
            dto.domicilio(),
            categoria);

    donador.setEstado(estado);

    return donador;
  }

  public List<CambioCategoriaResponse> toHistorialCategorias(Donador donador) {
    return donador.getHistorialCategorias().stream()
            .map(cambio -> new CambioCategoriaResponse(
                    cambio.getCategoria(), FechasUtc.formatear(cambio.getDesde())))
            .toList();
  }
  public QuejaDTO toQuejaDTO(Queja queja) {
    return new QuejaDTO(
            queja.getId(),
            queja.getDonacionID(),
            queja.getDonadorID(),
            queja.getFecha(),
            queja.getDescripcion());
  }

  public Queja toQueja(QuejaDTO quejaDTO) {
    return new Queja(
            quejaDTO.id(),
            quejaDTO.donacionID(),
            quejaDTO.donadorID(),
            quejaDTO.fecha(),
            quejaDTO.descripcion());
  }
  public EntidadBenefica toEntidadBenefica(EntidadBeneficaDTO dto) {
    return new EntidadBenefica(
            dto.id(),
            dto.razonSocial(),
            dto.domicilio(),
            dto.telefono(),
            dto.correo());
  }

  public EntidadBeneficaDTO toEntidadBeneficaDTO(EntidadBenefica entidad) {
    return new EntidadBeneficaDTO(
            entidad.getId(),
            entidad.getRazonSocial(),
            entidad.getDomicilio(),
            entidad.getTelefono(),
            entidad.getCorreo());
  }

  public NecesidadMaterial toNecesidadMaterial(NecesidadMaterialDTO dto) {
    return new NecesidadMaterial(
            dto.id(),
            dto.entidadID(),
            dto.nivelDeUrgencia(),
            dto.descripcion(),
            dto.cantidadObjetivo(),
            dto.productoSolicitadoID(),
            dto.tipo());
  }

  public NecesidadMaterialDTO toNecesidadMaterialDTO(NecesidadMaterial necesidad) {
    return new NecesidadMaterialDTO(
            necesidad.getId(),
            necesidad.getEntidadID(),
            necesidad.getNivelDeUrgencia(),
            necesidad.getDescripcion(),
            necesidad.getCantidadObjetivo(),
            necesidad.getProductoSolicitadoID(),
            necesidad.getTipo());
  }

  /**
   * Respuesta enriquecida con la cantidad ya cubierta, necesaria para el matchmaking de Logística.
   */
  public NecesidadResponse toNecesidadResponse(NecesidadMaterial necesidad) {
    return new NecesidadResponse(
            necesidad.getId(),
            necesidad.getEntidadID(),
            necesidad.getNivelDeUrgencia(),
            necesidad.getDescripcion(),
            necesidad.getCantidadObjetivo(),
            necesidad.getCantidadCubierta() != null ? necesidad.getCantidadCubierta() : 0,
            necesidad.getProductoSolicitadoID(),
            necesidad.getTipo());
  }
}
