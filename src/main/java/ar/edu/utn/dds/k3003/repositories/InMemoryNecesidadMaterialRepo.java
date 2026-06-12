package ar.edu.utn.dds.k3003.repositories;


import java.util.List;


public interface InMemoryNecesidadMaterialRepo implements NecesidadMaterialRepository{

  private List<NecesidadMaterial> necesidades;
  private AtomicLong idSecuencial = new AtomicLong(1);

  public InMemoryNecesidadMaterialRepo() {
    this.necesidades = new ArrayList<>();
       
  }

   
   @Override
   Optional<NecesidadMaterial> findById(String id){
       return this.necesidades.stream().filter(n -> n.getId().equals(id)).findFirst();
   }


  @Override
  NecesidadMaterial save(NecesidadMaterial necesidadMaterial)
  {
    NecesidadMaterial necesidadConID = necesidadMaterial;
    necesidadConID.setId(String.valueOf(idSecuencial.getAndIncrement()));

    this.necesidades.add(necesidadConID);
    return this.findById(necesidadConID.getId()).get();
  }

  Override
  NecesidadMaterial deleteById(String id)
  {
      NecesidadMaterial necesidad = findById(id);
      return this.necesidades.remove(necesidad);
  }

  @Override
  List<NecesidadMaterial> findAll()
  {
      return this.necesidades;
      
  }
  
}


           
}
