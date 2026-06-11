package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NecesidadesJpaRepository extends JpaRepository<NecesidadMaterial, String>, NecesidadesRepository {

  @Override
  default NecesidadMaterial removeById(String id) {
    if (!existsById(id)) {
      throw new RuntimeException("No existe una necesidad con ese ID");
    }
    NecesidadMaterial n = getReferenceById(id);
    delete(n);
    return n;
  }
}
