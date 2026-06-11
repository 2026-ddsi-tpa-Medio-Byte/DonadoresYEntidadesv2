package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.model.Donador;
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
            dto.categoria() != null ? dto.categoria() : "Colaborador";

    Donador donador = new Donador(
            dto.id(),
            dto.nombre(),
            dto.apellido(),
            dto.edad(),
            dto.email(),
            dto.nroDocumento(),
            dto.domicilio());

    donador.setEstado(estado);
    donador.setCategoria(categoria);

    return donador;
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
}
