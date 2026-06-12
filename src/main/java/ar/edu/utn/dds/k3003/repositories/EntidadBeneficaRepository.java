package ar.edu.utn.dds.k3003.repositories;


import java.util.List;


public interface EntidadBeneficaRepository{

    Optional<EntidadBenefica> findById(String id);

    EntidadBenefica save(EntidadBenefica entidadBenefica);

    EntidadBenefica deleteById(String id);

    List<EntidadBenefica> findAll();
  
}
