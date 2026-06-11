package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import java.util.List;
import java.util.Optional;

public interface NecesidadesRepository {
    NecesidadMaterial save(NecesidadMaterial necesidadMaterial);
    Optional<NecesidadMaterial> findById(String id);
    NecesidadMaterial removeById(String id);
    List<NecesidadMaterial> findAll();
}