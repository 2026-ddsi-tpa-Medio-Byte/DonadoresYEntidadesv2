package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntidadesJpaRepository extends JpaRepository<EntidadBenefica, String>, EntidadesRepository {

  @Override
  default EntidadBenefica removeById(String id) {
    if (!existsById(id)) {
      throw new RuntimeException("No existe una entidad con ese ID");
    }
    EntidadBenefica entidad = getReferenceById(id);
    delete(entidad);
    return entidad;
  }
}
