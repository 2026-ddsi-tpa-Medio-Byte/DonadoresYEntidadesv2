package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Donador;
import java.util.List;
import java.util.Optional;

public interface DonadoresRepository {
  // findById and save are NOT declared here to avoid ambiguity with JpaRepository/CrudRepository.
  // InMemory implementations provide them directly; JPA gets them from JpaRepository.
  // We use a bridge approach: Fachada receives this interface, but calls are resolved
  // at runtime through the concrete implementation.
  Donador removeById(String id);
  List<Donador> findAll();
  Optional<Donador> findById(String id);
  Donador save(Donador donador);
}
