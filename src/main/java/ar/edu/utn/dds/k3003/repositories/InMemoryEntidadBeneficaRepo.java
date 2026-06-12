package ar.edu.utn.dds.k3003.repositories;


import java.util.List;


public class InMemoryEntidadBeneficaRepo implements EntidadBeneficaRepository{

  private List<EntidadBenefica> entidades;
  private AtomicLong idSecuencial = new AtomicLong(1);

  public InMemoryEntidadBeneficaRepo() {
    this.entidades = new ArrayList<>();
  }

   
   @Override
   Optional<EntidadBenefica> findById(String id){
       return this.donaciones.stream().filter(q -> q.getId().equals(id)).findFirst();
   }


  @Override
  EntidadBenefica save(EntidadBenefica entidadBenefica)
  {
    EntidadBenefica entidadConID = entidadBenefica;
    entidadConID.setId(String.valueOf(idSecuencial.getAndIncrement()));

    this.entidades.add(entidadConID);
    return this.findById(entidadConID.getId()).get();
  }

  @Override
  EntidadBenefica deleteById(String id)
  {
      EntidadBenefica entidad = findById(id);
      return this.entidades.remove(entidad);
    
  }

  @Override
  List<EntidadBenefica> findAll()
  {
      return this.entidades;
  }


  
  }
