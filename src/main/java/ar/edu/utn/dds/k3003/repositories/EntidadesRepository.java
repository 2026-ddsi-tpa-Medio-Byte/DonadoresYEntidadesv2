package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import java.util.List;
import java.util.Optional;

public interface EntidadesRepository {
    EntidadBenefica save(EntidadBenefica entidadBenefica);
    Optional<EntidadBenefica> findById(String id);
    EntidadBenefica removeById(String id);
    List<EntidadBenefica> findAll();
}