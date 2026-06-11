package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import java.util.List;
import java.util.Optional;

public interface NecesidadesRepository {
    Optional<NecesidadMaterial> findById(String id);

    NecesidadMaterial save(NecesidadMaterial necesidad);

    List<NecesidadMaterial> findAll();

    NecesidadMaterial removeById(String id);
}