package ar.edu.utn.dds.k3003.repositories;


import java.util.List;


public class InMemoryQuejaRepo implements QuejasRepository{

  private List<Queja> quejas;
  private AtomicLong idSecuencial = new AtomicLong(1);

  public InMemoryQuejaRepo() {
    this.quejas = new ArrayList<>();
  }

   
   @Override
   Optional<Queja> findById(String id){
       return this.quejas.stream().filter(q -> q.getId().equals(id)).findFirst();
   }


  @Override
  Queja save(Queja queja)
  {
    Queja quejaConID = queja;
    quejaConID.setId(String.valueOf(idSecuencial.getAndIncrement()));

    this.quejas.add(quejaConID);
    return this.findById(quejaConID.getId()).get();
  }


  
  @Override
  List<Queja> findAll()
  {
      return this quejas;
  }

  
}
