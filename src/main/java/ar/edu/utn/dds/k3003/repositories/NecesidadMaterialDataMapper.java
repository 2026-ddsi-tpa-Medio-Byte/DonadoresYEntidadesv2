package ar.edu.utn.dds.k3003;


import java.util.List;


public class NecesidadMaterialDataMapper
  {
     public NecesidadMaterialDTO toNecesidadMaterialDTO(NecesidadMaterial necesidadMaterial)
    {
         return new NecesidadMaterialDTO(
             necesidadMaterial.getID(),
             necesidadMaterial.getEntidadID(),
             necesidadMaterial.getNivelDeUrgencia(),
             necesidadMaterial.getDescripcion(),
           necesidadMaterial.getCantidadObjetivo(),
           necesidadMaterial.getProductoSolicitado(),
           necesidadMaterial.getTipo()
           
         );

    }
    
    public NecesidadMaterial toNecesidadMaterial(NecesidadMaterialDTO necesidadMaterialDTO)
    {
        necesidadMaterialDTO.ID(),
          necesidadMaterialDTO.EntidadID(),
          necesidadMaterialDTO.NivelDeUrgencia(),
          necesidadMaterialDTO.Descripcion(),
          necesidadMaterialDTO.CantidadObjetivo(),
       necesidadMaterialDTO.ProductoSolicitado(),
       necesidadMaterialDTO.Tipo());

    }

  }
