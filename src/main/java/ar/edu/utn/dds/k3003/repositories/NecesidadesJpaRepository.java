package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NecesidadesJpaRepository extends JpaRepository<NecesidadMaterial, String>, NecesidadesRepository {
  @Override
  default NecesidadMaterial removeById(String id) {
    NecesidadMaterial n = findById(id)
        .orElseThrow(() -> new RuntimeException("No existe una necesidad con ese ID"));
    delete(n);
    return n;
  }
}
