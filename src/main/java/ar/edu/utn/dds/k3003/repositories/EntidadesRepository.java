package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import java.util.List;
import java.util.Optional;

public interface EntidadesRepository {
    Optional<EntidadBenefica> findById(String id);

    EntidadBenefica save(EntidadBenefica entidad);

    List<EntidadBenefica> findAll();

    EntidadBenefica removeById(String id);
}