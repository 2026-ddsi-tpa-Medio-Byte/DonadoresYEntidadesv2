package ar.edu.utn.dds.k3003.repositories;


import java.util.List;


public class InMemoryDonacionRepo implements DonacionRepository{

  private List<Donacion> donaciones;
  private AtomicLong idSecuencial = new AtomicLong(1);

  public InMemoryDonacionRepo() {
    this.donaciones = new ArrayList<>();
  }

   
   @Override
   Optional<Donacion> findById(String id){
       return this.donaciones.stream().filter(q -> q.getId().equals(id)).findFirst();
   }


  @Override
  Donacion save(Donacion donacion)
  {
    Donacion donacionConID = donacion;
    donacionConID.setId(String.valueOf(idSecuencial.getAndIncrement()));
    
    this.donaciones.add(donacionConID);
    return this.findById(donacionConID.getId()).get();
  }

  @Override
  Donacion deleteById(String id)
  {
      Donacion donacion = findById(id);
      return this.donaciones.remove(donacion);
    
  }

  @Override
  List<Donacion> findAll()
  {
      return this.donaciones;
  }
  
  }
