package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import java.util.List;

public interface NecesidadesRepository {
    List<NecesidadMaterial> findAll();
    NecesidadMaterial removeById(String id);
}