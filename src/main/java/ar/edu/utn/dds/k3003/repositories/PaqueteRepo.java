package ar.edu.utn.dds.k3003.repositories;


import java.util.List;


public interface PaqueteRepo{

    Optional<Paquete> findById(String id);

    Paquete save(Paquete paquete);

    Paquete deleteById(String id);
        
    List<Paquete> findAll();
    
  
}
