package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import java.util.NoSuchElementException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NecesidadesJpaRepository extends JpaRepository<NecesidadMaterial, String>, NecesidadesRepository {

  @Override
  default NecesidadMaterial removeById(String id) {
    // NoSuchElementException y no RuntimeException: el handler la traduce a 404, y quien borra
    // tiene que poder distinguir "no existe" de "pedido inválido".
    if (!existsById(id)) {
      throw new NoSuchElementException("No existe una necesidad con ese ID");
    }
    NecesidadMaterial n = getReferenceById(id);
    delete(n);
    return n;
  }
}
