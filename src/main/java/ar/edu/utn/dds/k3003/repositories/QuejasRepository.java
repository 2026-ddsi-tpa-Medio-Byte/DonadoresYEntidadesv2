package ar.edu.utn.dds.k3003.repositories;


import java.util.List;
import java.util.Optional;


public interface QuejasRepository{

   Optional<Queja> findById(String id);

   Queja save(Queja queja);

   

   List<Queja> findAll();

}
