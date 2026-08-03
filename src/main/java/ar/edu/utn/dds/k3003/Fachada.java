package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import ar.edu.utn.dds.k3003.clients.LogisticaStockClient;
import ar.edu.utn.dds.k3003.controllers.responses.NecesidadResponse;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.DonadorYaExistenteException;
import ar.edu.utn.dds.k3003.model.Donador;
import ar.edu.utn.dds.k3003.model.Queja;
import ar.edu.utn.dds.k3003.repositories.*;
import lombok.val;
import ar.edu.utn.dds.k3003.MetricasService;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Fachada implements FachadaDonadoresYEntidades {

  private DonadoresRepository donadoresRepository;
  private EntidadesRepository entidadesRepository;
  private NecesidadesRepository necesidadesRepository;

  private static final org.slf4j.Logger log =
          org.slf4j.LoggerFactory.getLogger(Fachada.class);

  @Autowired(required = false)
  private FachadaIncentivos fachadaIncentivos;

  @Autowired(required = false)
  private FachadaDonaciones fachadaDonaciones;

  @Autowired(required = false)
  private LogisticaStockClient logisticaStockClient;

  private final DonadoresYEntidadesDataMapper donadoresYEntidadesDataMapper =
          new DonadoresYEntidadesDataMapper();

  private Integer ultimoIdDonador = 0;
  private Integer ultimoIdEntidad = 0;
  private Integer ultimoIdNecesidad = 0;
  private Integer ultimoIdQueja = 0;
  private final List<Queja> quejas = new ArrayList<>();
  private final MetricasService metricasService;

  /** Constructor sin argumentos para tests que usan new Fachada() o reflection. */
  public Fachada() {
    this.donadoresRepository = new InMemoryDonadoresRepo();
    this.entidadesRepository = new InMemoryEntidadesRepo();
    this.necesidadesRepository = new InMemoryNecesidadesRepo();
    this.metricasService = new MetricasService(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
  }

  @Autowired
  public Fachada(
          DonadoresRepository donadoresRepository,
          EntidadesRepository entidadesRepository,
          NecesidadesRepository necesidadesRepository,
          MetricasService metricasService) {
    this.donadoresRepository = donadoresRepository;
    this.entidadesRepository = entidadesRepository;
    this.necesidadesRepository = necesidadesRepository;
    this.metricasService = metricasService;
  }

  

  @Override
  public DonadorDTO agregarDonador(DonadorDTO donadorDTO) {
    if (donadorDTO.apellido() == null || donadorDTO.nombre() == null || donadorDTO.edad() == null || donadorDTO.email() == null
            || donadorDTO.nroDocumento() == null || donadorDTO.domicilio() == null) {
      metricasService.incrementarDonadoresErrores();
      throw new RuntimeException("El donador no puede ser null");
    }

    String id = donadorDTO.id();
    if (id == null) {
      this.ultimoIdDonador++;
      id = String.valueOf(this.ultimoIdDonador);
    }

    if (this.donadoresRepository.findById(id).isPresent()) {
      metricasService.incrementarDonadoresErrores();
      throw new DonadorYaExistenteException("Ya existe un donador con ese ID");
    }

    DonadorDTO dtoConId =
            new DonadorDTO(
                    id,
                    donadorDTO.nombre(),
                    donadorDTO.apellido(),
                    donadorDTO.edad(),
                    donadorDTO.email(),
                    donadorDTO.nroDocumento(),
                    donadorDTO.domicilio(),
                    donadorDTO.estado(),
                    donadorDTO.categoria());

    val donador = donadoresYEntidadesDataMapper.toDonador(dtoConId);
    val donadorGuardado = this.donadoresRepository.save(donador);
    metricasService.incrementarDonadoresRegistrados();
    return donadoresYEntidadesDataMapper.toDonadorDTO(donadorGuardado);
  }

  @Override
  public DonadorDTO buscarDonadorPorID(String donadorID) throws NoSuchElementException {
    val donadorOptional = this.donadoresRepository.findById(donadorID);

    if (donadorOptional.isEmpty()) {
      throw new DonadorNoEncontradoException("No existe un donador con ese ID");
    }

    return donadoresYEntidadesDataMapper.toDonadorDTO(donadorOptional.get());
  }

  @Override
  public DonadorDTO modificarEstado(String donadorID, EstadoDonadorEnum estado)
          throws NoSuchElementException {
    if (estado == null) {
      throw new RuntimeException("El estado no puede ser null");
    }

    val donadorOptional = this.donadoresRepository.findById(donadorID);

    if (donadorOptional.isEmpty()) {
      throw new DonadorNoEncontradoException("No existe un donador con ese ID");
    }

    val donadorFinal = donadorOptional.get();
    donadorFinal.setEstado(estado);

    this.donadoresRepository.save(donadorFinal);

    return donadoresYEntidadesDataMapper.toDonadorDTO(donadorFinal);
  }

  @Override
  public DonadorDTO modifcarCategoria(String donadorID, String categoria)
          throws NoSuchElementException {
    if (categoria == null) {
      throw new RuntimeException("La categoría no puede ser null");
    }

    val donadorOptional = this.donadoresRepository.findById(donadorID);

    if (donadorOptional.isEmpty()) {
      throw new DonadorNoEncontradoException("No existe un donador con ese ID");
    }

    val donadorFinal = donadorOptional.get();
    donadorFinal.setCategoria(categoria);

    this.donadoresRepository.save(donadorFinal);

    return donadoresYEntidadesDataMapper.toDonadorDTO(donadorFinal);
  }

  @Override
  public List<NecesidadMaterialDTO> obtenerNecesidadesInsatisfechasDe(String productoSolicitadoID) {
    return this.necesidadesRepository.findAll().stream()
            .filter(necesidad -> necesidad.getProductoSolicitadoID().equals(productoSolicitadoID))
            .filter(necesidad -> !necesidad.estaSatisfecha())
            .map(donadoresYEntidadesDataMapper::toNecesidadMaterialDTO)
            .toList();
  }

  /**
   * Igual que {@link #obtenerNecesidadesInsatisfechasDe(String)} pero devolviendo la respuesta
   * enriquecida con {@code cantidadActual}, que Logística necesita para el matchmaking.
   */
  public List<NecesidadResponse> obtenerNecesidadesInsatisfechasDetalladasDe(
          String productoSolicitadoID) {
    return this.necesidadesRepository.findAll().stream()
            .filter(necesidad -> necesidad.getProductoSolicitadoID().equals(productoSolicitadoID))
            .filter(necesidad -> !necesidad.estaSatisfecha())
            .map(donadoresYEntidadesDataMapper::toNecesidadResponse)
            .toList();
  }

  /** Busca una necesidad por ID devolviendo la respuesta con {@code cantidadActual}. */
  public NecesidadResponse buscarNecesidadPorID(String necesidadID) {
    val necesidadOptional = this.necesidadesRepository.findById(necesidadID);
    if (necesidadOptional.isEmpty()) {
      throw new NoSuchElementException("No existe una necesidad con ese ID");
    }
    return donadoresYEntidadesDataMapper.toNecesidadResponse(necesidadOptional.get());
  }

  @Override
  public void setFachadaIncentivos(FachadaIncentivos fachadaIncentivos) {
    this.fachadaIncentivos = fachadaIncentivos;
  }

  /** Inyección de la fachada de Donaciones (para validar productos en Entrega 4). */
  public void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones) {
    this.fachadaDonaciones = fachadaDonaciones;
  }

  /** Inyección del cliente de Logística (para consultar stock y asignar en Entrega 4). */
  public void setLogisticaStockClient(LogisticaStockClient logisticaStockClient) {
    this.logisticaStockClient = logisticaStockClient;
  }

  @Override
  public Boolean puedeDonar(String donadorID) throws NoSuchElementException {
    val donadorOptional = this.donadoresRepository.findById(donadorID);

    if (donadorOptional.isEmpty()) {
      throw new DonadorNoEncontradoException("No existe un donador con ese ID");
    }

    return donadorOptional.get().puedeDonar();
  }

  @Override
  public List<QuejaDTO> obtenerQuejasDe(String donadorID) throws NoSuchElementException {
    val donadorOptional = this.donadoresRepository.findById(donadorID);

    if (donadorOptional.isEmpty()) {
      throw new DonadorNoEncontradoException("No existe un donador con ese ID");
    }

    return this.quejas.stream()
            .filter(q -> q.getDonadorID() != null && q.getDonadorID().equals(donadorID))
            .map(donadoresYEntidadesDataMapper::toQuejaDTO)
            .toList();
  }

  @Override
  public NecesidadMaterialDTO satisfacerNecesidad(String necesidadID, Integer cantidad)throws NoSuchElementException {
    val necesidadOptional = this.necesidadesRepository.findById(necesidadID);

    if (necesidadOptional.isEmpty()) {
      throw new RuntimeException("No existe una necesidad con ese ID");
    }

    val necesidad = necesidadOptional.get();
    necesidad.registrarSatisfaccion(cantidad);

    this.necesidadesRepository.save(necesidad);
    metricasService.incrementarNecesidadesSatisfechas();
    return donadoresYEntidadesDataMapper.toNecesidadMaterialDTO(necesidad);
  }

  @Override
  public DonadorStatsDTO estadisticasDonador(String donadorID) {
    val donadorOptional = this.donadoresRepository.findById(donadorID);

    if (donadorOptional.isEmpty()) {
      throw new RuntimeException("No existe un donador con ese ID");
    }

    val donador = donadorOptional.get();

    List<String> insigniasID = List.of();
    String misionActualID = null;

    if (this.fachadaIncentivos != null) {
      try {
        insigniasID =
                this.fachadaIncentivos.getInsigniasDeDonador(donadorID).stream()
                        .map(insignia -> insignia.id())
                        .toList();
      } catch (Exception ignored) {
        insigniasID = List.of();
      }

      try {
        MisionDTO mision = this.fachadaIncentivos.getMisionEnCursoDeDonador(donadorID);
        if (mision != null) {
          misionActualID = mision.id();
        }
      } catch (Exception ignored) {
        misionActualID = null;
      }
    }

    return new DonadorStatsDTO(
            donador.getId(),
            donador.getNombre(),
            donador.getApellido(),
            donador.getEdad(),
            donador.getEstado(),
            donador.getCategoria(),
            misionActualID,
            insigniasID);
  }

  @Override
  public EntidadBeneficaDTO agregarEntidad(EntidadBeneficaDTO entidadBeneficaDTO) {
    if (entidadBeneficaDTO == null) {
      throw new RuntimeException("La entidad no puede ser null");
    }

    String id = entidadBeneficaDTO.id();
    if (id == null) {
      this.ultimoIdEntidad++;
      id = String.valueOf(this.ultimoIdEntidad);
    }

    if (this.entidadesRepository.findById(id).isPresent()) {
      throw new RuntimeException("Ya existe una entidad con ese ID");
    }

    EntidadBeneficaDTO dtoConId =
            new EntidadBeneficaDTO(
                    id,
                    entidadBeneficaDTO.razonSocial(),
                    entidadBeneficaDTO.domicilio(),
                    entidadBeneficaDTO.telefono(),
                    entidadBeneficaDTO.correo());

    val entidad = donadoresYEntidadesDataMapper.toEntidadBenefica(dtoConId);
    val entidadGuardada = this.entidadesRepository.save(entidad);
    metricasService.incrementarEntidadesRegistradas();
    return donadoresYEntidadesDataMapper.toEntidadBeneficaDTO(entidadGuardada);
  }

  @Override
  public EntidadBeneficaDTO buscarEntidadPorID(String entidadID) throws NoSuchElementException {
    val entidadOptional = this.entidadesRepository.findById(entidadID);

    if (entidadOptional.isEmpty()) {
      throw new RuntimeException("No existe una entidad con ese ID");
    }

    return donadoresYEntidadesDataMapper.toEntidadBeneficaDTO(entidadOptional.get());
  }

  @Override
  public NecesidadMaterialDTO registrarNecesidad(NecesidadMaterialDTO necesidadMaterialDTO) {
    if (necesidadMaterialDTO == null) {
      throw new RuntimeException("La necesidad no puede ser null");
    }

    // Entrega 4: corroborar con Donaciones que el producto solicitado exista.
    // Guardado por null: con el constructor sin-args (tests de cátedra) no se valida.
    if (this.fachadaDonaciones != null && necesidadMaterialDTO.productoSolicitadoID() != null) {
      try {
        log.info(
            "[Donadores -> Donaciones] Validando producto de necesidad (productoID={})",
            necesidadMaterialDTO.productoSolicitadoID());
        this.fachadaDonaciones.buscarProductoPorID(necesidadMaterialDTO.productoSolicitadoID());
        log.info(
            "[Donadores <- Donaciones] producto valido (productoID={})",
            necesidadMaterialDTO.productoSolicitadoID());
      } catch (Exception e) {
        metricasService.incrementarNecesidadesErrores();
        log.warn(
            "[Donadores] Necesidad rechazada: producto inexistente en Donaciones (productoID={})",
            necesidadMaterialDTO.productoSolicitadoID());
        throw new RuntimeException(
            "El producto solicitado no existe en Donaciones: "
                + necesidadMaterialDTO.productoSolicitadoID());
      }
    }

    String id = necesidadMaterialDTO.id();
    if (id == null) {
      this.ultimoIdNecesidad++;
      id = String.valueOf(this.ultimoIdNecesidad);
    }

    if (this.necesidadesRepository.findById(id).isPresent()) {
      throw new RuntimeException("Ya existe una necesidad con ese ID");
    }

    NecesidadMaterialDTO dtoConId =
            new NecesidadMaterialDTO(
                    id,
                    necesidadMaterialDTO.entidadID(),
                    necesidadMaterialDTO.nivelDeUrgencia(),
                    necesidadMaterialDTO.descripcion(),
                    necesidadMaterialDTO.cantidadObjetivo(),
                    necesidadMaterialDTO.productoSolicitadoID(),
                    necesidadMaterialDTO.tipo());

    val necesidad = donadoresYEntidadesDataMapper.toNecesidadMaterial(dtoConId);
    val necesidadGuardada = this.necesidadesRepository.save(necesidad);
    metricasService.incrementarNecesidadesRegistradas();

    // Entrega 4: consultar stock en Logística y, si hay, asignar al momento (origen = Donadores).
    // Best-effort y guardado por null (no rompe la creación ni los tests de cátedra).
    if (this.logisticaStockClient != null && necesidadGuardada.getProductoSolicitadoID() != null) {
      int stock =
          logisticaStockClient.consultarStockDisponible(necesidadGuardada.getProductoSolicitadoID());
      int aAsignar = Math.min(stock, necesidadGuardada.cantidadFaltante());
      if (aAsignar > 0) {
        logisticaStockClient.solicitarAsignacion(
            necesidadGuardada.getId(), necesidadGuardada.getProductoSolicitadoID(), aAsignar);
        try {
          necesidadGuardada.registrarSatisfaccion(aAsignar);
          this.necesidadesRepository.save(necesidadGuardada);
          metricasService.incrementarNecesidadesSatisfechas();
        } catch (RuntimeException e) {
          log.warn(
              "[Donadores] No se pudo registrar satisfacción local de la necesidad {}: {}",
              necesidadGuardada.getId(),
              e.getMessage());
        }
      }
    }

    return donadoresYEntidadesDataMapper.toNecesidadMaterialDTO(necesidadGuardada);
  }

  @Override
  public QuejaDTO agregarQueja(QuejaDTO quejaDTO) throws NoSuchElementException {
    if (quejaDTO == null) {
      throw new RuntimeException("La queja no puede ser null");
    }

    String idGenerado = quejaDTO.id();
    if (idGenerado == null || idGenerado.isBlank()) {
      this.ultimoIdQueja++;
      idGenerado = String.valueOf(this.ultimoIdQueja);
    }

    final String idQueja = idGenerado;

    boolean yaExiste = this.quejas.stream()
            .anyMatch(q -> q.getId() != null && q.getId().equals(idQueja));

    if (yaExiste) {
      throw new RuntimeException("Ya existe una queja con ese ID");
    }

    QuejaDTO dtoConId =
            new QuejaDTO(
                    idQueja,
                    quejaDTO.donacionID(),
                    quejaDTO.donadorID(),
                    quejaDTO.fecha(),
                    quejaDTO.descripcion());

    Queja queja = donadoresYEntidadesDataMapper.toQueja(dtoConId);

    this.quejas.add(queja);

    val donadorOptional = this.donadoresRepository.findById(quejaDTO.donadorID());
    if (donadorOptional.isPresent()) {
      Donador donador = donadorOptional.get();
      donador.agregarQueja(queja);
      this.donadoresRepository.save(donador);
    }
    metricasService.incrementarQuejasRegistradas();
    return donadoresYEntidadesDataMapper.toQuejaDTO(queja);
  }

  public List<DonadorDTO> buscarTodosLosDonadores() {
    return this.donadoresRepository.findAll().stream()
            .map(donadoresYEntidadesDataMapper::toDonadorDTO)
            .toList();
  }

  public List<EntidadBeneficaDTO> buscarTodasLasEntidades() {
    return this.entidadesRepository.findAll().stream()
            .map(donadoresYEntidadesDataMapper::toEntidadBeneficaDTO)
            .toList();
  }

  // ── Entrega 4: operaciones adicionales (consulta/edición/baja) usadas por el bot ──
  // Nota: buscarNecesidadPorID está definido más arriba y devuelve NecesidadResponse
  // (con cantidadActual), que es lo que consume Logística para el matchmaking.

  public void eliminarNecesidad(String necesidadID) {
    this.necesidadesRepository.removeById(necesidadID);
  }

  public NecesidadMaterialDTO modificarNecesidad(String necesidadID, NecesidadMaterialDTO dto) {
    val necesidadOptional = this.necesidadesRepository.findById(necesidadID);
    if (necesidadOptional.isEmpty()) {
      throw new RuntimeException("No existe una necesidad con ese ID");
    }
    val necesidad = necesidadOptional.get();
    if (dto.nivelDeUrgencia() != null) necesidad.setNivelDeUrgencia(dto.nivelDeUrgencia());
    if (dto.descripcion() != null) necesidad.setDescripcion(dto.descripcion());
    if (dto.cantidadObjetivo() != null) necesidad.setCantidadObjetivo(dto.cantidadObjetivo());
    if (dto.productoSolicitadoID() != null) {
      necesidad.setProductoSolicitadoID(dto.productoSolicitadoID());
    }
    if (dto.tipo() != null) necesidad.setTipo(dto.tipo());
    this.necesidadesRepository.save(necesidad);
    return donadoresYEntidadesDataMapper.toNecesidadMaterialDTO(necesidad);
  }

  public EntidadBeneficaDTO editarEntidad(String entidadID, EntidadBeneficaDTO dto) {
    val entidadOptional = this.entidadesRepository.findById(entidadID);
    if (entidadOptional.isEmpty()) {
      throw new RuntimeException("No existe una entidad con ese ID");
    }
    val entidad = entidadOptional.get();
    if (dto.razonSocial() != null) entidad.setRazonSocial(dto.razonSocial());
    if (dto.domicilio() != null) entidad.setDomicilio(dto.domicilio());
    if (dto.telefono() != null) entidad.setTelefono(dto.telefono());
    if (dto.correo() != null) entidad.setCorreo(dto.correo());
    this.entidadesRepository.save(entidad);
    return donadoresYEntidadesDataMapper.toEntidadBeneficaDTO(entidad);
  }
}
