package ar.edu.utn.dds.k3003;


import java.util.List;


public class FachadaDonadores implements FachadaDonadoresYEntidades {

  private NecesidadMaterialRepository necesidadMaterialRepository;
  private QuejaRepository quejaRepository;
  private DonadorStatsDataMapper donadorStatsDataMapper;
  private EntidadBeneficaRepository entidadBeneficaRepository;
  private DonadorRepository donadorRepository;
  private DonadorDataMapper donadorDataMapper;
  private DonadorstatsRepository donadorstatsRepository;
  private EntidadBeneficaDataMapper entidadBeneficaDataMapper;
  private DonadorstatsDataMapper = donadorstatsDataMapper;
  private NecesidadMaterialDataMapper necesidadMaterialDataMapper;
  private QuejaDataMapper quejaDataMapper;

  private FachadaIncentivos fachadaIncentivos;
  private FachadaDonaciones fachadaDonaciones;

  private AtomicLong idSecuencialDonador = new AtomicLong(1);
  private AtomicLong idSecuencialDonadorstats = new AtomicLong(1);
  private AtomicLong idSecuencialQueja = new AtomicLong(1);
  private AtomicLong idSecuencialNecesidadMaterial = new AtomicLong(1);
  
  public FachadaDonadoresYEntidades(){
       super();
    
       necesidadMaterialRepository = new InMemoryNecesidadMaterialRepo();
       necesidadMaterialDataMapper = new NecesidadMaterialDataMapper();
       quejaRepository = new InMemoryQuejaRepo();
       quejaDataMapper = new QuejaDataMapper();
       donadorRepository = new InMemoryDonadorRepo();
       donadorstatsRepository = new InMemoryDonadorstatsRepo();
       entidadBeneficaRepository = new InMemoryEntidadRepo();
       entidadBeneficaDataMapper = new EntidadBeneficaDataMapper();
       donadorstatsDataMapper = new DonadorstatsDataMapper();
       donadorDataMapper = new DonadorDataMapper();
       
}

  @Override
  EntidadBeneficaDTO agregarEntidad(EntidadBeneficaDTO entidadBeneficaDTO){
      val entidad = this.entidadBeneficaDataMapper.toEntidadBenefica(entidadBeneficaDTO);
       val entidadGuardada = this.entidadBeneficaRepository.save(entidad);
       return this.entidadBeneficaDataMapper.toEntidadBeneficaDTO(entidadGuardada);
  }
  
  @Override
  EntidadBeneficaDTO buscarEntidadPorID(String entidadID) throws NoSuchElementException
  {
      val entidad = this.entidadentidadBeneficaRepository.findById(entidadID);
      return this.entidadentidadBeneficaDataMapper.toEntidadDTO(entidad);
  }
  
  @Override
  public NecesidadMaterialDTO registrarNecesidad(NecesidadMaterialDTO necesidadMaterialDTO){
     
    
    val necesidad = this.necesidadDataMapper.toNecesidad(necesidadMaterialDTO);
    necesidad.setID(String.valueOf(idSecuencialDonador.getAndIncrement()));
    val necesidadGuardada = this.necesidadRepository.save(necesidad);
    return this.necesidadDataMapper.toNecesidadDTO(necesidadGuardada);

  }

  @Override
  QuejaDTO agregarQueja(QuejaDTO quejaDTO) throws NoSuchElementException
  {
    
    val queja = this.quejaDataMapper.toQueja(quejaDTO);
    queja.SetID(String.valueOf(idSecuencialQueja.getAndIncrement()));
    val quejaGuardada = this.quejaRepository.save(queja);
    
    return this.quejaDataMapper.toQuejaDTO(quejaGuardada);
    
  }

  @Override
  Boolean puedeDonar(String donadorID) throws NoSuchElementException{
     
    
  }

  @Override
  List<QuejaDTO> obtenerQuejasDe(String donadorID) throws NoSuchElementException{
    
   val quejasDeDonador = this.quejaRepository.findAll().stream().filter(q -> q.getDonadorId().equals(donadorID)).collect(Collectors.toList());
   List<QuejaDTO> quejasDeDonadorDTO = new ArrayList<>();
    for(val queja : quejasDeDonador)
     {
         quejasDeDonadorDTO.add(this.quejaDataMapper.toQuejaDTO(queja));
     }
    
   return quejasDeDonadorDTO;
    
  }

  @Override
  DonadorDTO modificarEstado(String donadorID, EstadoDonadorEnum estado)
      throws NoSuchElementException{

          DonadorDTO donadorDTO = buscarDonadorPorId(donadorID);
          val donador = this.donadorDataMapper.toDonador(donadorDTO);
          if(obtenerQuejasDe(donadorID).size >= 5 && estado == EstadoDonadorEnum.VERIFICADO)
          donador.setEstado(EstadoDonadorEnum.SOSPECHOSO);
          if(obtenerQuejasDe(donadorID).size >= 10 && estado == EstadoDonadorEnum.SOSPECHOSO)
          donador.setEstado(EstadoDonadorEnum.BANEADO);

          this.donadorRepository.deleteById(donadorDTO.ID());
          val donadorGuardado = this.donadorRepository.save(donador);
        
          return this.donadorDataMapper.toDonadorDTO(donadorGuardado);
        
    }

    @Override
    DonadorDTO modifcarCategoria(String donadorID,String categoria) throws NoSuchElementException
    {
        DonadorDTO donadorDTO = buscarDonadorPorID(donadorID);
        val donador = this.donadorDataMapper.toDonador(donadorDTO);
        if(categoria == "OCASIONAL" )
        {
           val donaciones = this.fachadaDonaciones.getDonacionesRepository().findAll().filter(d -> d.getDonadorID().equals(donadorID)).collect(Collectors.toList());
             
           List<String> categoriasRepetidas = new ArrayList<>();
           for(val donacion : donaciones) 
           {
             
           ProductoDTO productoDTO = this.fachadaDonacion.buscarProductoPorID(donacion.getProductoID());
           categoriasRepetidas.add(this.fachadaDonaciones.getCategoriaRepo().findById(productoDTO.CategoriaID()).getNombre());
             
           }
           
           List<String> setCategorias = eliminarDuplicados(categoriasRepetidas);
           if(setCategorias.size() >= 3){
                 donador.setCategoria("COLABORADOR");
           }
          
           this.donadorRepository.deleteById(donadorDTO.ID());
           val donadorGuardado = this.donadorRepository.save(donador);
           return this.donadorDataMapper.toDonadorDTO(donadorGuardado);
      }
      if(categoria = "COLABORADOR")
      {
           DonadorDTO donadorDTO = buscarDonadorPorID(donadorID);
           val donador = this.donadorDataMapper.toDonador(donadorDTO);
           
           List<Queja> quejass = new ArrayList<>();
           val donaciones = this.fachadaDonaciones.getDonacionesRepo().findAll().filter(d -> d.getDonadorID().equals(donadorID)).collect(Collectors.toList()));
           if(donaciones.size() >= 20)
           {
             
               for(var donacion : donaciones)
               {
                   val quejas = this.fachadaDonaciones.getQuejaRepo().findAll().stream().filter(q -> q.getDonacionID().equals(donacion.getID())).collect(Collectors.toList());
                   quejass.add(quejas);
             
               }
               if(quejass.size() == 0)
               {
                    donador.setCategoria("TRANSFORMADOR");
                    
               }
             
               this.donadorRepository.deleteById(donadorDTO.ID());
               val donadorGuardado = this.donadorRepository.save(donador);
               return this.donadorDataMapper.toDonadorDTO(donadorGuardado);
             
           }
                                                                                                                                              
      }
      if(categoria == "TRANSFORMADOR")
      {
          DonadorDTO donadorDTO = buscarDonadorPorID(donadorID);
           val donador = this.donadorDataMapper.toDonador(donadorDTO);
           int cantProducto = 0;
           List<Queja> quejass = new ArrayList<>();
           val donaciones = this.fachadaDonaciones.getDonacionesRepo().findAll().filter(d -> d.getDonadorID().equals(donadorID)).collect(Collectors.toList()));
           if(donaciones.size() >= 10)
           {
                for(val donacion : donaciones)
                  {
                      ProductoDTO productoDTO = this.fachadaDonacion.buscarProductoPorID(donacion.getProductoID());
                      cantProducto = cantProducto + productoDTO.Cantidad();

                  }
                if(cantProducto >= 50)
                {
                    donador.setCategoria("REVOLUCIONARIO");
               
                }

                this.donadorRepository.deleteById(donadorDTO.ID());
                val donadorGuardado = this.donadorRepository.save(donador);
                return this.donadorDataMapper.toDonadorDTO(donadorGuardado);
          
           }


      }
        
      return donadorDTO;
    
    }
  
   @Override
   List<NecesidadMaterialDTO> obtenerNecesidadesInsatisfechasDe(String productoSolicitadoID)
    {
        
        val necesidades = necesidadRepository.findAll().stream().filter(n -> n.getProductoSolicitadoID().equals(productoSolicitadoID)).collect(Collectors.toList());
        List<NecesidadMaterialDTO> necesidadesDTO = new ArrayList<>();
        for(val necesidad : necesidades)
           {
               if(necesidad.getCantidadObjetivo() >= 0)
               necesidadesDTO.add(necesidadDataMapper.toNecesidadDTO(necesidad));
           }
      
        return necesidadesDTO;
      
    }


    @Override
    NecesidadMaterialDTO satisfacerNecesidad(String necesidadID, Integer cantidad)
      throws NoSuchElementException{
        
          val necesidad = this.necesidadRepository.findById(necesidadID);
          int cantAnt = necesidad.getCantidadObjetivo();
          if(cantidad <= cantAnt && cantidad >= 0)
          {
             necesidad.setCantidadObjetivo(cantAnt-cantidad);
          }
        
          return this.necesidadDataMapper.toNecesidadDTO(necesidad);
      }
  
  @Override
  DonadorStatsDTO estadisticasDonador(String donadorID){
        
       DonadorDTO donadorDTO = buscarDonadorPorID(donadorID);
       DonadorStats donadorStats = new donadorStats();
       donadorStats.setID(String.valueOf(idSecuencialDonadorstats.getAndIncrement()));
       donadorStats.setEstado(donadorDTO.Estado());
       donadorStats.setCategoria(donadorDTO.Categoria());
       
       List<Insignia> insignias = this.fachadaIncentivos.getInsigniasDeDonador(donadorID);
       List<String> insigniasID = insigniasDTO.stream().map(Insignia::getID).collect(Collectors.toList());
       Mision mision = this.fachadaIncentivos.getMisionEnCursoDeDonador(donadorID);

       donadorStats.setInsigniasID(insigniasID);
       donadorStats.setMisionActualID(mision.getID());
       val donadorstatsGuardado = this.donadorstatsRepository.save(donadorstats);
         
       return this.donadorStatsDataMapper.toDonadorStatsDTO(donadorstatsGuardado);

  }

  @Override
  void setFachadaIncentivos(FachadaIncentivos fachadaIncentivos){

       this.fachadaIncentivos = fachadaIncentivos;
  }

  List<String> eliminarDuplicados(List<String> categoriasDuplicadas)
  {
     List<String> setCategorias = categoriasDuplicadas.stream().distinct().toList();
     return setCategorias;
  }

  public DonadorStatsRepository getStatsRepo()
    {
        return this.donadorstatsrepository;
    }
  public DonadorRepository getDonadorRepo()
  {
        return this.donadorRepository;
  }
  public List<NecesidadMaterialDTO> buscarNecesidades()
         {
               List<NecesidadMaterialDTO> necesidadesDTO = new ArrayList<>();
               for(val necesidad : this.necesidadMaterialRepository.findAll())
               {
                   necesidadesDTO.add(this.necesidadMaterialDataMapper.toNecesidadMaterialDTO(necesidad);

                }
           
               return necesidadesDTO;
         }
   public List<EntidadBeneficaDTO> buscarEntidades()
   {
      List<EntidadBeneficaDTO> entidadesDTO = new ArrayList<>();
               for(val entidad : this.entidadBeneficaRepository.findAll())
               {
                   entidadesDTO.add(this.entidadBeneficaDataMapper.toEntidadBeneficaDTO(entidad);

                }
           
               return entidadesDTO;

   }
                                      
  }
