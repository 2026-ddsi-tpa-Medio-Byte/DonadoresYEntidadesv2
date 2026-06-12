package ar.edu.utn.dds.k3003;


import java.util.List;


public class ImplFachadaIncentivos{
      
      private MisionRepository misionRepo;
      private MisionDataMapper misionDataMapper;
      private InsigniaRepository insigniaRepo;
      private InsigniaDataMapper insigniaDataMapper;
      
      
      private FachadaDonacion fachadaDonacion;
      private FachadaDonador fachadaDonador;
      private FachadaLogistica fachadaLogistica;
      
      public ImplFachadaIncentivos()
      {
           super();
           this.misionRepo = new InMemoryMisionRepo();
           this.misionDataMapper = new MisionDataMapper();
           this.insigniaRepo = new InMemoryInsigniaRepo();
           this.insigniaDataMapper = new InsigniaDataMapper();
            
            this.fachadaLogistica = new FachadaLogistica();
            this.fachadaDonacion = new FachadaDonacion();
            this.fachadaDonador = new FachadaDonador();
           
      }

      @Override
      List<InsigniaDTO> getInsigniasDeDonador(String donadorID) throws NoSuchElementException
      {
          val donadorStats = this.fachadaDonador.getStatsRepo().findById(donadorID);
            DonadorStatsDTO donadorStatsDTO = this.fachadaDonador.getDataMapper().toDonadorStatsDTO(donadorStats);
            List<InsigniaDTO> InsigniasDTO = New ArrayList<>();
           for(val insigniaid : donadorStatsDTO.InsigniasID())
            {
                 InsigniasDTO.add(this.insigniaDataMapper.toInsigniaDTO(insigniaid));
            }
            
            return InsigniasDTO;
            
      }
      
      @Override
      MisionDTO getMisionEnCursoDeDonador(String donadorID) throws NoSuchElementException{
          val donadorStats = this.fachadaDonador.getStatsRepo().findById(donadorID);
          DonadorStatsDTO = this.fachadaDonador.getDataMapper().toDonadorStatsDTO(donadorStats);
          MisionDTO misionDTO = this.misionesRepository.findById(donadorStatsDTO.MisionActual());
          return misionDTO;
      }
      
      @Override
      void asignarMisionADonador(String donadorID, MisionDTO misionDTO) throws NoSuchElementException
       {
            
            DonadorStatsDTO donadorStatsDTO = this.fachadaDonador.estadisticasDonador(donadorID);
            val donadorStats = this.fachadaDonador.getDataMapper().toDonadorStats(donadorStatsDTO);
             donadorStats.setMisionActualID(misionDTO.getID());
            val donadorStatsGuardado = this.fachadaDonador.getRepo().save(donadorStats);
       }

      @Override
      void asignarInsigniaADonador(String donadorID, InsigniaDTO insigniaDTO)
      throws NoSuchElementException{

          DonadorStatsDTO donadorStatsDTO = this.fachadaDonador.estadisticasDonador(donadorID);
          val donadorStats = this.fachadaDonador.getDataMapper().toDonadorStats(donadorStatsDTO);
          donadorStats.getInsigniasID().add(insigniaDTO.ID());
            val donadorStatsGuardado = this.fachadaDonador.getRepo().save(donadorStats);
            
            
      }

      @Override
      void procesarDonador(String donadorID) throws NoSuchElementException{

           DonadorDTO donadorDTO = this.fachadaDonador.buscarDonadorPorID(donadorID);
           List<Donacion> donaciones = this.fachadaDonacion.getRepo().findAll().stream().filter(d -> d.getDonadorID().equals(donadorID)).collect(Collectors.toList());
           for(val donacion : donaciones)
                  {
                      verificarExistenciaMision(donadorDTO);
                      listarPorFecha(donaciones,fechaActual);

                  }
            
      }
      

       @Override
       void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones){
           this.fachadaDonacion = fachadaDonaciones;
       }

      @Override
      void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades){
           this.fachadaDonador = fachadaDonadores;

      }

      
      void verificarExistenciaMision(DonadorDTO donadorDTO){

          if(this.fachadaDonador.getRepo().findById(donadorDTO.ID()).isPresent())
            {  
                  throw new DonadorExisteMisionException("procesar donador existe , mision actual " + getMisionEnCursoDeDonador(donadorDTO.ID()).Nombre());
            }

            if(this.fachadaDonador.getRepo().findById(donadorDTO.ID()).isEmpty())
            {
                  throw new DonadorNoExisteException(" procesar donador, no existe donador ");
            }
            
      }

            void listarPorFecha(List<Donacion> donaciones,LocalDate fechaActual)
            {
                List<LocalDate> fechas = donaciones.stream().map(Donacion::getFecha).collect(Collectors.toList());
                List<LocalDate> fechasFiltradas = fechas.stream().filter(f -> f <= fechaActual).collect(Collectors.toList());
                System.Out.Println("listado de fechas hasta hoy");
                for(val fecha : fechaFiltradas){
                      
                    System.Out.Println(fecha);
                      
                }
                  
            }
      
       
      }

                  
