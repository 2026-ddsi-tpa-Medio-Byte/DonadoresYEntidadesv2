package ar.edu.utn.dds.k3003;


import java.util.List;


public class EntidadBeneficaDataMapper{

    public EntidadBeneficaDTO toEntidadBeneficaDTO(EntidadBenefica entidadBenefica)
    {
         return new EntidadBeneficaDTO(
              entidadBenefica.getID(),
               entidadBenefica.getRazonSocial(),
              entidadBenefica.getDomicilio(),
              entidadBenefica.getTelefono(),
              entidadBenefica.getCorreo()
  
              ); }
  public EntidadBenefica toEntidadBenefica(EntidadBeneficaDTO entidadBeneficaDTO)
    {
         return new EntidadBenefica(
              entidadBeneficaDTO.ID(),
             entidadBeneficaDTO.RazonSocial(),
             entidadBeneficaDTO.Domicilio(),
             entidadBeneficaDTO.Telefono(),
             entidadBeneficaDTO.Correo()
             
             
         );

    }



    
}
