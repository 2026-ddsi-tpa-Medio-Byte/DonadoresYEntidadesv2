package ar.edu.utn.k3003.repositories;


import java.util.List;


public interface NecesidadMaterialRepo{

    Optional<NecesidadMaterial> findById(String id);

    NecesidadMaterial save(NecesidadMaterial necesidadMaterial);

    NecesidadMaterial deleteById(String id);

    List<NecesidadMaterial> findAll();


}
