package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Donador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DonadoresJpaRepository extends JpaRepository<Donador, String>, DonadoresRepository {

  @Override
  default Donador removeById(String id) {
    if (!existsById(id)) {
      throw new RuntimeException("No existe un donador con ese ID");
    }
    Donador donador = getReferenceById(id);
    delete(donador);
    return donador;
  }
}
