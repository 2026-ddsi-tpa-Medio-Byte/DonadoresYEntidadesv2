package ar.edu.utn.dds.k3003.repositories;


import java.util.List;


public class InMemoryPaqueteRepo implements PaqueteRepository{

  private List<Paquete> paquetes;
  private AtomicLong idSecuencial = new AtomicLong(1);

  public InMemoryPaqueteRepo() {
    this.paquetes = new ArrayList<>();
  }

   
   @Override
   Optional<Paquete> findById(String id){
       return this.paquetes.stream().filter(p -> p.getId().equals(id)).findFirst();
   }


  @Override
  Paquete save(Paquete paquete)
  {
    Paquete paqueteConID = paquete;
    paqueteConID.setId(String.valueOf(idSecuencial.getAndIncrement()));

    this.paquetes.add(paqueteConID);
    return this.findById(paqueteConID.getId()).get();
  }

  Override
  Paquete deleteById(String id)
  {
      Paquete paquete = findById(id);
      return this.paquete.remove(paquete);
  }

  @Override
  List<Paquete> findAll()
  {
      return this.paquetes;
  }
  
                                      }
