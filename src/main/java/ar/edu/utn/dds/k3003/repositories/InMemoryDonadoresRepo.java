package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Donador;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryDonadoresRepo implements DonadoresRepository {

  private List<Donador> donadores;

  public InMemoryDonadoresRepo() {
    this.donadores = new ArrayList<>();
  }

  @Override
  public Optional<Donador> findById(String id) {
    return this.donadores.stream().filter(d -> d.getId().equals(id)).findFirst();
  }

  @Override
  public Donador save(Donador donador) {
    this.donadores.removeIf(d -> d.getId().equals(donador.getId()));
    this.donadores.add(donador);
    return donador;
  }

  @Override
  public Donador removeById(String id) {
    val donador = this.findById(id);
    if (donador.isEmpty()) {
      throw new RuntimeException("No existe un donador con ese ID");
    }
    this.donadores.remove(donador.get());
    return donador.get();
  }

  @Override
  public List<Donador> findAll() {
    return new ArrayList<>(this.donadores);
  }
}
