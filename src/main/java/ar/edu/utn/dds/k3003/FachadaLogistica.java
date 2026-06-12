package ar.edu.utn.dds.k3003;


import java.util.List;


public class ImplFachadaLogistica{

  private AsignacionRepository asignacionRepo;
  private AsignacionDataMapper asignacionDataMapper;
  private DepositoRepository depositoRepo;
  private DepositoDataMapper depositoDataMapper;
  private PaqueteRepo paqueteRepo;
  private PaqueteDeoo paqueteDepo;

  
  private FachadaDonacion fachadaDonacion;
  private FachadaDonadoresYEntidades fachadaDonadores;
  
  
  public class ImplFachadaLogistica(){
       super();
       this.depositoRepo = new InMemoryDepositoRepo();
       this.asignacionRepo = new InMemoryAsignacionRepo();
       this.paqueteRepo = new InMemoryPaqueteRepo();
  }

  @Override
  AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException
  {
       val asignacion = this.asignacionRepo.findAll().stream().filter(a -> a.getPaqueteID().equals( paqueteID)).findFirst(); 
       return this.asignacionDataMapper.toAsignacionDTO(asignacion);
  }
    
  @Override
  DepositoDTO gestionarDonacion(
      String depositoID, String donacionID, String productoID, Integer cantidad)
      throws NoSuchElementException
  {
      
    
  }

  @Override
  void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo){

      DepositoDTO depositoDTO = buscarDepositoPorID(depositoID);
 val deposito = = this.depositoDataMapper.toDeposito(depositoDTO);
      deposito.setAlgoritmo(tipoAlgoritmo);
      val depositoGuardado = this.depositoRepo.save(deposito);
    
  }

  @Override
  AsignacionDTO ejecutarMatchmaking(
      String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidades){

      Asignacion asignacion = new Asignacion();
      DepositoDTO depositoDTO = buscarDepositoPorID(paqueteDTO.DepositoID());
  val deposito = this.depositoDataMapper.toDeposito(depositoDTO);
      int cantidadDonada = 0;
      List<Necesidad_Resto> restoCantidad = new ArrayList<>();
      for(val paquete : deposito.getStockActual())
      {
          cantidadDonada = cantidadDonada + paquete.getCantidad();
      }
    
      for(val necesidad : necesidades)
      {
          if(necesidad.getCantidadObjetivo())
          {
             restoCantidad.add(cantidadDonada-necesidad.getCantidadObjetivo());
             
          }
      }
    
      int menorResto = 0;
      menorResto = Collections.min(restoCantidad);
      Necesidad_Resto ne_R = restoCantidad.stream().filter(rc -> rc.getResto().equals(menorResto)).findFirst();
    
      asignacion.setPaqueteID(paqueteDTO.ID());
      asignacion.setNecesidadID(ne_R.getNecesidadID());
      
      val asignacionGuardada = this.asignacionRepository.save(asignacion);
      val depositoGuardado = this.depositoRepository.save(deposito);
      return this.asignacionDataMapper.toAsignacionDTO(asignacionGuardada);
  }
  
  @Override
  void reportarEntrega(PaqueteDTO paqueteDTO){
    
      DepositoDTO depositoDTO = buscarDepositoPorID(paqueteDTO.getDepositoID());
      
      if(depositoDTO.CantidadMaxima() == depositoDTO.StockActual().size())
      {
          val depositoNuevo = new Deposito();
          depositoNuevo.setID(depositoDTO.ID()+1);
          depositoNuevo.setAlgoritmo(depositoDTO.Algoritmo());
          depositoNuevo.setNombre(depositoDTO.Nombre());
          depositoNuevo.setDireccion(depositoDTO.Direccion());
          depositoNuevo.setCapacidadMaxima(depositoDTO.CapacidadMaxima());
          depositoNuevo.setStockActual(new ArrayList<>());
          depositoNuevo.getStockActual().add(this.paqueteDataMapper.toPaquete(PaqueteDTO));
          val depositoNuevoGuardado = this.depositoRepository.save(depositoNuevo);
         
          
          AsignacionDTO asignacionDTO = buscarAsignacionPorPaqueteID(paqueteDTO.ID());
          val asignacion = this.asignacionDataMapper.toAsignacion(asignacionDTO);
          val asignacionGuardada = this.asignacionRepo.save(asignacion);
          
            
          NecesidadMaterialDTO necesidadMaterialDTO = this.fachadaDonadores.satisfacerNecesidad(asignacionDTO.NecesidadID(),paqueteDTO.Cantidad());
          val necesidad = this.fachadaDonadores.getNecesidadDataMapper().toNecesidad(necesidadMaterialDTO);
          val necesidadGuardada = this.fachadaDonadores.getNecesidadMaterialRepository().save(necesidad);
          
      }else{
      
      val = this.depositoDataMapper.toDeposito(depositoDTO);
      val paquete = this.paqueteDataMapper.toPaquete(paqueteDTO);
      
      deposito.getStockActual().add(paquete);
      this.depositoRepository.save(this.depositoDataMapper.toDeposito(depositoDTO));
      
      AsignacionDTO asignacionDTO = buscarAsignacionPorPaqueteID(paqueteDTO);
       val asignacion = this.asignacionDataMapper.toAsignacion(asignacionDTO);
      val asignacionGuardada = this.asignacionRepo.save(asignacion);
        
      NecesidadMaterialDTO necesidadMaterialDTO = this.fachadaDonadores.satisfacerNecesidad(asignacionDTO.NecesidadID(),paqueteDTO.Cantidad());
      val necesidad = this.fachadaDonadores.getNecesidadDataMapper().toNecesidad(necesidadMaterialDTO);
      val necesidadGuardada = this.fachadaDonadores.getNecesidadRepository().save(necesidad);
     
      }
      
  }

  @Override
  void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades){

      this.fachadaDonadores = fachadaDonadoresYEntidades;

      
  }
  
  @Override
  void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones){
      
      this.fachadafachadaDonacion = fachadaDonaciones;

    
  }
  
  
    }
