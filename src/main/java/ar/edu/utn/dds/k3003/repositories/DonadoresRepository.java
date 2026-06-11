package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Donador;
import java.util.List;
import java.util.Optional;

public interface DonadoresRepository {
  List<Donador> findAll();
  Donador removeById(String id);
}