package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EntidadesJpaRepository extends JpaRepository<EntidadBenefica, String>, EntidadesRepository {

    @Override
    default Optional<EntidadBenefica> findById(String id) {
        return JpaRepository.super.findById(id);
    }

    @Override
    default EntidadBenefica save(EntidadBenefica entidad) {
        return JpaRepository.super.save(entidad);
    }

    @Override
    default EntidadBenefica removeById(String id) {
        EntidadBenefica e = findById(id)
            .orElseThrow(() -> new RuntimeException("No existe una entidad con ese ID"));
        delete(e);
        return e;
    }
}