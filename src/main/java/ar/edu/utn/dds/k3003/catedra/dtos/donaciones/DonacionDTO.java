package ar.edu.utn.dds.k3003.catedra.dtos.donaciones;

public record DonacionDTO(
    String id,
    String donadorID,
    String depositoID,
    String descripcion,
    String productoID,
    Integer cantidad,
    EstadoDonacionEnum estado) {}
