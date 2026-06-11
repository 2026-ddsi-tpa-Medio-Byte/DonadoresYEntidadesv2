package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Donador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DonadoresJpaRepository extends JpaRepository<Donador, String>, DonadoresRepository {
  @Override
  default Donador removeById(String id) {
    Donador d = findById(id)
        .orElseThrow(() -> new RuntimeException("No existe un donador con ese ID"));
    delete(d);
    return d;
  }
}
