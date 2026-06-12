package ar.edu.utn.dds.k3003.repositories;


import java.util.List;
import java.util.Optional;


public interface DonadorRepository{
  
  Optional<Donador> findById(String id);

  Donador save(Donador donador);

  Donador deleteById(String id);

  Donador findAll();

}
