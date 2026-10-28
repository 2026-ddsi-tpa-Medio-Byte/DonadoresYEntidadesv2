package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.InsigniaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import ar.edu.utn.dds.k3003.clients.LogisticaStockClient;
import ar.edu.utn.dds.k3003.controllers.responses.CambioCategoriaResponse;
import ar.edu.utn.dds.k3003.controllers.responses.NecesidadResponse;
import ar.edu.utn.dds.k3003.eventos.EventoDonadores;
import ar.edu.utn.dds.k3003.eventos.EventosDonadores;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.DonadorYaExistenteException;
import ar.edu.utn.dds.k3003.model.Donador;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.model.Queja;
import ar.edu.utn.dds.k3003.repositories.*;
import lombok.val;
import ar.edu.utn.dds.k3003.MetricasService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.function.Function;

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

  /**
   * Los eventos para el modelo de lectura (Entrega 6). Guardado por null como los clientes de
   * arriba: con el constructor sin argumentos de los tests de cátedra no se emite nada.
   */
  @Autowired(required = false)
  private EventosDonadores eventos;

  private final DonadoresYEntidadesDataMapper donadoresYEntidadesDataMapper =
          new DonadoresYEntidadesDataMapper();

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

  

  /**
   * Vacía por completo la base del módulo. Como los IDs se derivan del máximo existente, al quedar
   * las tablas vacías la numeración vuelve a empezar en 1. Las quejas y los cambios de estado se
   * eliminan en cascada junto con sus donadores.
   */
  @org.springframework.transaction.annotation.Transactional
  public void resetBaseDeDatos() {
    this.necesidadesRepository.deleteAll();
    this.entidadesRepository.deleteAll();
    this.donadoresRepository.deleteAll();
    log.info("[Donadores] Base de datos reseteada");
    // Es el único método con transacción propia: el evento sale recién cuando se confirma el
    // borrado. Si se deshiciera, Consultas no borraría un modelo que sigue vigente.
    emitirEvento(EventosDonadores::moduloReseteado);
  }

  // ── Eventos (Entrega 6) ─────────────────────────────────────────────────────
  // Publicar nunca hace fallar la operación: para cuando se emite, el cambio ya está guardado.
  // PublicadorEventos ya no deja escapar nada; esto cubre lo que pueda fallar antes de llegar ahí,
  // como armar los datos.

  /** Inyección del catálogo de eventos, para los tests que arman la fachada a mano. */
  public void setEventos(EventosDonadores eventos) {
    this.eventos = eventos;
  }

  /** Arma el evento con el estado de este momento. Null si no hay eventos o si falló el armado. */
  private EventoDonadores armarEvento(Function<EventosDonadores, EventoDonadores> armado) {
    if (this.eventos == null) {
      return null;
    }
    try {
      return armado.apply(this.eventos);
    } catch (RuntimeException e) {
      metricasService.registrarEventoFallido(null);
      log.warn("[Donadores] No se pudo armar un evento para Consultas: {}", e.toString());
      return null;
    }
  }

  private void publicarEvento(EventoDonadores evento) {
    if (evento == null) {
      return;
    }
    try {
      this.eventos.emitir(evento);
    } catch (RuntimeException e) {
      metricasService.registrarEventoFallido(evento.sobre().tipo());
      log.warn(
          "[Donadores] No se pudo emitir {} de {}: {}",
          evento.sobre().tipo(),
          evento.sobre().entidadId(),
          e.toString());
    }
  }

  private void emitirEvento(Function<EventosDonadores, EventoDonadores> armado) {
    publicarEvento(armarEvento(armado));
  }

  /**
   * El estado actual de todo el módulo como eventos, para {@code POST /admin/republicar-eventos}.
   *
   * <p>Con los tipos de modificación y no de alta: para Consultas son lo mismo (un upsert), y así se
   * lee lo que es, una resincronización. Las quejas van al final, después de sus donadores.
   */
  public List<EventoDonadores> eventosDelEstadoActual() {
    if (this.eventos == null) {
      return List.of();
    }
    List<Donador> donadores = this.donadoresRepository.findAll();
    List<EventoDonadores> resultado = new ArrayList<>();
    donadores.forEach(d -> resultado.add(this.eventos.donadorModificado(d)));
    this.entidadesRepository.findAll()
        .forEach(e -> resultado.add(this.eventos.entidadModificada(e)));
    this.necesidadesRepository.findAll()
        .forEach(n -> resultado.add(this.eventos.necesidadModificada(n)));
    donadores.stream()
        .map(Donador::getQuejas)
        .filter(java.util.Objects::nonNull)
        .flatMap(List::stream)
        .forEach(q -> resultado.add(this.eventos.quejaRegistrada(q)));
    return resultado;
  }

  // ── Generación de IDs ───────────────────────────────────────────────────────
  // Los IDs se derivan del contenido persistido, no de contadores en memoria: un
  // contador en RAM se reinicia con cada deploy o reinicio del servicio mientras la
  // base conserva los registros, lo que provocaba "Ya existe una entidad con ese ID".

  private int parseIdOCero(String id) {
    if (id == null || id.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(id.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private String siguienteId(java.util.stream.Stream<String> idsExistentes) {
    int maximo = idsExistentes.mapToInt(this::parseIdOCero).max().orElse(0);
    return String.valueOf(maximo + 1);
  }

  private String siguienteIdDonador() {
    return siguienteId(this.donadoresRepository.findAll().stream().map(Donador::getId));
  }

  private String siguienteIdEntidad() {
    return siguienteId(this.entidadesRepository.findAll().stream().map(e -> e.getId()));
  }

  private String siguienteIdNecesidad() {
    return siguienteId(this.necesidadesRepository.findAll().stream().map(n -> n.getId()));
  }

  private String siguienteIdQueja() {
    return siguienteId(todasLasQuejas().stream().map(Queja::getId));
  }

  /** Todas las quejas persistidas, obtenidas a través de los donadores que las contienen. */
  private List<Queja> todasLasQuejas() {
    return this.donadoresRepository.findAll().stream()
            .map(Donador::getQuejas)
            .filter(java.util.Objects::nonNull)
            .flatMap(List::stream)
            .toList();
  }

  private static boolean estaVacio(String valor) {
    return valor == null || valor.isBlank();
  }

  /**
   * Los campos obligatorios que no vinieron, en el orden del DTO. Se informan todos juntos para que
   * quien da el alta los corrija en un solo intento. El email y la categoría no se validan más allá
   * de su presencia: el fixture de cátedra usa "donador1" en ambos e Incentivos manda categorías
   * propias por PATCH.
   */
  private static List<String> camposFaltantes(DonadorDTO dto) {
    List<String> faltantes = new ArrayList<>();
    if (estaVacio(dto.nombre())) faltantes.add("nombre");
    if (estaVacio(dto.apellido())) faltantes.add("apellido");
    if (dto.edad() == null) faltantes.add("edad");
    if (estaVacio(dto.email())) faltantes.add("email");
    if (estaVacio(dto.nroDocumento())) faltantes.add("nroDocumento");
    if (estaVacio(dto.domicilio())) faltantes.add("domicilio");
    return faltantes;
  }

  @Override
  public DonadorDTO agregarDonador(DonadorDTO donadorDTO) {
    if (donadorDTO == null) {
      metricasService.incrementarDonadoresErrores();
      throw new RuntimeException("No llegaron los datos del donador");
    }

    List<String> faltantes = camposFaltantes(donadorDTO);
    if (!faltantes.isEmpty()) {
      metricasService.incrementarDonadoresErrores();
      throw new RuntimeException(
          "Faltan datos obligatorios del donador: " + String.join(", ", faltantes));
    }

    if (donadorDTO.edad() <= 0) {
      metricasService.incrementarDonadoresErrores();
      throw new RuntimeException("La edad del donador debe ser mayor a 0");
    }

    String id = donadorDTO.id();
    if (id == null) {
      id = siguienteIdDonador();
    }

    if (this.donadoresRepository.findById(id).isPresent()) {
      metricasService.incrementarDonadoresErrores();
      throw new DonadorYaExistenteException("Ya existe un donador con ese ID");
    }

    // El documento identifica a la persona: con dos altas, sus quejas se repartirían entre dos
    // registros y ninguno llegaría al umbral de SOSPECHOSO.
    if (this.donadoresRepository.existsByNroDocumento(donadorDTO.nroDocumento())) {
      metricasService.incrementarDonadoresErrores();
      throw new DonadorYaExistenteException(
          "Ya existe un donador con el documento " + donadorDTO.nroDocumento());
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
    emitirEvento(e -> e.donadorCreado(donadorGuardado));
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

    // Pasa por el dominio y no por el setter para que el cambio quede en el historial, igual que
    // los automáticos por quejas; si no, un baneo manual no dejaba rastro de cuándo ocurrió.
    // El pedido no trae motivo, así que se deja uno fijo que lo distingue de los automáticos.
    val donadorFinal = donadorOptional.get();
    donadorFinal.modificarEstado(estado, "Cambio manual de estado");

    this.donadoresRepository.save(donadorFinal);
    // Siempre, aunque el estado sea el mismo: el dominio también lo registra como un cambio.
    emitirEvento(e -> e.donadorModificado(donadorFinal));

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

    // Es lo que llama Incentivos con su PATCH al completar una misión. Pasa por el dominio para que
    // el cambio quede en el historial de categorías, que pide la ficha del donador (consulta 1).
    val donadorFinal = donadorOptional.get();
    boolean cambio = donadorFinal.modificarCategoria(categoria);

    this.donadoresRepository.save(donadorFinal);
    // La categoría la publica Donadores aunque la cambie Incentivos: es el dueño del dato. Si no
    // cambió, no hay nada que contar.
    if (cambio) {
      emitirEvento(e -> e.donadorModificado(donadorFinal));
    }

    return donadoresYEntidadesDataMapper.toDonadorDTO(donadorFinal);
  }

  /** El historial de categorías del donador, de la más vieja a la actual. */
  public List<CambioCategoriaResponse> historialCategoriasDe(String donadorID) {
    val donadorOptional = this.donadoresRepository.findById(donadorID);

    if (donadorOptional.isEmpty()) {
      throw new DonadorNoEncontradoException("No existe un donador con ese ID");
    }

    return donadoresYEntidadesDataMapper.toHistorialCategorias(donadorOptional.get());
  }

  /**
   * Se puede registrar una necesidad sin producto: sin el chequeo de null, una sola necesidad así
   * hacía fallar con NPE la consulta entera que usa Logística para el matchmaking.
   */
  private static boolean esDelProducto(NecesidadMaterial necesidad, String productoSolicitadoID) {
    return necesidad.getProductoSolicitadoID() != null
        && necesidad.getProductoSolicitadoID().equals(productoSolicitadoID);
  }

  @Override
  public List<NecesidadMaterialDTO> obtenerNecesidadesInsatisfechasDe(String productoSolicitadoID) {
    return this.necesidadesRepository.findAll().stream()
            .filter(necesidad -> esDelProducto(necesidad, productoSolicitadoID))
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
            .filter(necesidad -> esDelProducto(necesidad, productoSolicitadoID))
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

    // Se leen del donador persistido, no de una lista en memoria: así las quejas
    // sobreviven a los reinicios del servicio.
    List<Queja> quejasDelDonador = donadorOptional.get().getQuejas();
    if (quejasDelDonador == null) {
      return List.of();
    }

    return quejasDelDonador.stream()
            .map(donadoresYEntidadesDataMapper::toQuejaDTO)
            .toList();
  }

  /**
   * Un candado por necesidad para que dos entregas simultáneas no se pisen.
   *
   * <p>Sin esto, satisfacerNecesidad hace leer-modificar-guardar sin protección: dos llamadas a
   * la vez leen la misma cantidadCubierta y la segunda pisa a la primera. Probado con diez
   * entregas concurrentes contra una necesidad de 20, quedaba en 8 o en 16 en vez de en 20.
   *
   * <p>Alcanza porque el servicio corre en una sola instancia. Si algún día se levanta más de
   * una, esto hay que reemplazarlo por un bloqueo en la base (SELECT FOR UPDATE o @Version).
   */
  private final java.util.concurrent.ConcurrentMap<String, Object> candadosPorNecesidad =
      new java.util.concurrent.ConcurrentHashMap<>();

  @Override
  public NecesidadMaterialDTO satisfacerNecesidad(String necesidadID, Integer cantidad)throws NoSuchElementException {
    Object candado = candadosPorNecesidad.computeIfAbsent(necesidadID, id -> new Object());

    EventoDonadores evento;
    NecesidadMaterialDTO resultado;
    synchronized (candado) {
      val necesidadOptional = this.necesidadesRepository.findById(necesidadID);

      if (necesidadOptional.isEmpty()) {
        throw new NoSuchElementException("No existe una necesidad con ese ID");
      }

      val necesidad = necesidadOptional.get();
      necesidad.registrarSatisfaccion(cantidad);

      this.necesidadesRepository.save(necesidad);
      metricasService.incrementarNecesidadesSatisfechas();
      // El evento se arma acá adentro: con el candado tomado, la cantidadCubierta es la que dejó
      // esta entrega y no una posterior, y la hora del evento sigue el mismo orden que las entregas.
      evento = armarEvento(e -> e.necesidadModificada(necesidad));
      resultado = donadoresYEntidadesDataMapper.toNecesidadMaterialDTO(necesidad);
    }
    // Y se publica afuera: sin transacción, el listener corre en este mismo hilo, y adentro del
    // bloque la espera de RabbitMQ (hasta el timeout de conexión si está caído) frenaría a todas las
    // entregas de esta necesidad. Afuera no cambia lo que se publica, que ya quedó armado.
    publicarEvento(evento);
    return resultado;
  }

  @Override
  public DonadorStatsDTO estadisticasDonador(String donadorID) {
    val donadorOptional = this.donadoresRepository.findById(donadorID);

    if (donadorOptional.isEmpty()) {
      throw new NoSuchElementException("No existe un donador con ese ID");
    }

    val donador = donadorOptional.get();

    List<String> insigniasID = List.of();
    String misionActualID = null;

    if (this.fachadaIncentivos != null) {
      insigniasID = insigniasSegunIncentivos(donadorID);
      misionActualID = misionSegunIncentivos(donadorID);
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

  // Las estadísticas tienen que salir aunque Incentivos esté caído: los datos del donador son
  // nuestros y siguen valiendo. Pero el fallo no puede pasar en silencio, porque una lista de
  // insignias vacía no se distingue de "todavía no ganó ninguna".

  private List<String> insigniasSegunIncentivos(String donadorID) {
    long inicio = System.nanoTime();
    try {
      List<InsigniaDTO> insignias = this.fachadaIncentivos.getInsigniasDeDonador(donadorID);
      metricasService.registrarLlamadaSaliente(
          "estadisticas_donador", "incentivos", "obtener_insignias", true,
          System.nanoTime() - inicio);
      return insignias == null ? List.of() : insignias.stream().map(InsigniaDTO::id).toList();
    } catch (Exception e) {
      metricasService.registrarLlamadaSaliente(
          "estadisticas_donador", "incentivos", "obtener_insignias", false,
          System.nanoTime() - inicio);
      log.warn(
          "[Donadores <- Incentivos] no se pudieron obtener las insignias (donadorID={}): {}",
          donadorID,
          e.getMessage());
      return List.of();
    }
  }

  private String misionSegunIncentivos(String donadorID) {
    long inicio = System.nanoTime();
    try {
      MisionDTO mision = this.fachadaIncentivos.getMisionEnCursoDeDonador(donadorID);
      metricasService.registrarLlamadaSaliente(
          "estadisticas_donador", "incentivos", "obtener_mision", true,
          System.nanoTime() - inicio);
      return mision == null ? null : mision.id();
    } catch (Exception e) {
      metricasService.registrarLlamadaSaliente(
          "estadisticas_donador", "incentivos", "obtener_mision", false,
          System.nanoTime() - inicio);
      log.warn(
          "[Donadores <- Incentivos] no se pudo obtener la misión en curso (donadorID={}): {}",
          donadorID,
          e.getMessage());
      return null;
    }
  }

  @Override
  public EntidadBeneficaDTO agregarEntidad(EntidadBeneficaDTO entidadBeneficaDTO) {
    if (entidadBeneficaDTO == null) {
      throw new RuntimeException("La entidad no puede ser null");
    }

    // Es el nombre con el que se reconoce a la entidad al elegir dónde colgar una necesidad: sin
    // razón social queda un registro que nadie puede identificar.
    if (estaVacio(entidadBeneficaDTO.razonSocial())) {
      throw new RuntimeException("La razón social de la entidad es obligatoria");
    }

    String id = entidadBeneficaDTO.id();
    if (id == null) {
      id = siguienteIdEntidad();
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
    emitirEvento(e -> e.entidadCreada(entidadGuardada));
    return donadoresYEntidadesDataMapper.toEntidadBeneficaDTO(entidadGuardada);
  }

  @Override
  public EntidadBeneficaDTO buscarEntidadPorID(String entidadID) throws NoSuchElementException {
    val entidadOptional = this.entidadesRepository.findById(entidadID);

    if (entidadOptional.isEmpty()) {
      throw new NoSuchElementException("No existe una entidad con ese ID");
    }

    return donadoresYEntidadesDataMapper.toEntidadBeneficaDTO(entidadOptional.get());
  }

  /**
   * Corrobora con Donaciones que el producto exista, y si no, rechaza la operación.
   *
   * <p>Lo comparten el alta y la modificación de necesidades: si cada una tuviera su copia, la
   * regla podría divergir y cambiar el producto sería una forma de saltearse la validación del
   * alta. Guardado por null: con el constructor sin argumentos (tests de cátedra) no se valida.
   *
   * @param operacion la etiqueta con la que se cuenta la llamada saliente en las métricas
   */
  private void validarProductoEnDonaciones(String productoID, String operacion) {
    if (this.fachadaDonaciones == null || productoID == null) {
      return;
    }
    long inicioValidacion = System.nanoTime();
    try {
      log.info(
          "[Donadores -> Donaciones] Validando producto de necesidad (productoID={})", productoID);
      this.fachadaDonaciones.buscarProductoPorID(productoID);
      metricasService.registrarLlamadaSaliente(
          operacion, "donaciones", "validar_producto", true,
          System.nanoTime() - inicioValidacion);
      log.info("[Donadores <- Donaciones] producto valido (productoID={})", productoID);
    } catch (Exception e) {
      metricasService.registrarLlamadaSaliente(
          operacion, "donaciones", "validar_producto", false,
          System.nanoTime() - inicioValidacion);
      metricasService.incrementarNecesidadesErrores();
      log.warn(
          "[Donadores] Necesidad rechazada: producto inexistente en Donaciones (productoID={})",
          productoID);
      throw new RuntimeException("El producto solicitado no existe en Donaciones: " + productoID);
    }
  }

  @Override
  public NecesidadMaterialDTO registrarNecesidad(NecesidadMaterialDTO necesidadMaterialDTO) {
    if (necesidadMaterialDTO == null) {
      throw new RuntimeException("La necesidad no puede ser null");
    }

    // Una necesidad de 0 unidades (o negativa) queda satisfecha de entrada y ensucia el
    // matchmaking de Logística, que la toma como candidata sin tener nada que asignarle.
    if (necesidadMaterialDTO.cantidadObjetivo() == null
        || necesidadMaterialDTO.cantidadObjetivo() <= 0) {
      metricasService.incrementarNecesidadesErrores();
      throw new RuntimeException("La cantidad objetivo de la necesidad debe ser mayor a 0");
    }

    // Mismo criterio que con el producto: sin esto quedaban necesidades huérfanas,
    // colgadas de una entidad que no existe.
    if (necesidadMaterialDTO.entidadID() != null
        && this.entidadesRepository.findById(necesidadMaterialDTO.entidadID()).isEmpty()) {
      metricasService.incrementarNecesidadesErrores();
      throw new NoSuchElementException(
          "No existe una entidad con ese ID: " + necesidadMaterialDTO.entidadID());
    }

    validarProductoEnDonaciones(necesidadMaterialDTO.productoSolicitadoID(), "registrar_necesidad");

    String id = necesidadMaterialDTO.id();
    if (id == null) {
      id = siguienteIdNecesidad();
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

    try {
      // Entrega 4: consultar stock en Logística y, si hay, asignar al momento (origen = Donadores).
      // Best-effort y guardado por null (no rompe la creación ni los tests de cátedra).
      if (this.logisticaStockClient != null
          && necesidadGuardada.getProductoSolicitadoID() != null) {
        asignarStockDisponible(necesidadGuardada);
      }
    } finally {
      // Un solo NECESIDAD_REGISTRADA, con la cubierta final, y no uno en 0 seguido de un
      // NECESIDAD_MODIFICADA: el alta y la asignación del stock son una sola operación (Prueba 6,
      // "la necesidad se crea ya cubierta"), y el modelo de lectura no tiene por qué mostrar un 0/20
      // que nunca fue el resultado de nada. Se relee de la base porque asignarStockDisponible puede
      // haber registrado la satisfacción en el objeto sin llegar a guardarla, y el contrato no deja
      // publicar lo que no se guardó. En el finally para que ni una falla inesperada en el paso
      // best-effort se lleve el evento de un alta que ya está guardada.
      emitirEvento(e -> e.necesidadRegistrada(
          this.necesidadesRepository.findById(necesidadGuardada.getId()).orElse(necesidadGuardada)));
    }

    return donadoresYEntidadesDataMapper.toNecesidadMaterialDTO(necesidadGuardada);
  }

  /**
   * Pide a Logística el stock que ya tenga del producto y lo da por cubierto en la necesidad.
   *
   * <p>La satisfacción local al crear la necesidad es a propósito: así se presentó en la Prueba 6
   * de la Entrega 4 y así figura en el diagrama del flujo 5. Pero solo se registra lo que Logística
   * confirmó; si no, cada módulo contaría una cantidad distinta para la misma necesidad. Cualquier
   * falla deja la necesidad creada con 0 cubierto: el alta no depende de Logística.
   */
  private void asignarStockDisponible(NecesidadMaterial necesidad) {
    String productoID = necesidad.getProductoSolicitadoID();

    long inicioStock = System.nanoTime();
    OptionalInt stock = logisticaStockClient.consultarStockDisponible(productoID);
    metricasService.registrarLlamadaSaliente(
        "registrar_necesidad", "logistica", "consultar_stock", stock.isPresent(),
        System.nanoTime() - inicioStock);
    if (stock.isEmpty()) {
      log.warn(
          "[Donadores] Necesidad {} creada sin asignar: Logística no informó el stock de {}",
          necesidad.getId(),
          productoID);
      return;
    }

    int faltante = necesidad.cantidadFaltante();
    int aAsignar = Math.min(stock.getAsInt(), faltante);
    if (aAsignar <= 0) {
      return;
    }

    // Una RECURRENTE no acepta asignación parcial. Si se pidiera igual, Logística apartaría esas
    // unidades y después registrarSatisfaccion las rechazaría: quedaban asignadas de un lado y sin
    // registrar del otro.
    if (necesidad.getTipo() == TipoNecesidadMaterialEnum.RECURRENTE && aAsignar < faltante) {
      log.info(
          "[Donadores] Necesidad {} RECURRENTE sin asignar: el stock ({}) no cubre lo que falta ({})",
          necesidad.getId(),
          aAsignar,
          faltante);
      return;
    }

    long inicioAsignacion = System.nanoTime();
    boolean confirmada =
        logisticaStockClient.solicitarAsignacion(necesidad.getId(), productoID, aAsignar);
    metricasService.registrarLlamadaSaliente(
        "registrar_necesidad", "logistica", "solicitar_asignacion", confirmada,
        System.nanoTime() - inicioAsignacion);
    if (!confirmada) {
      log.warn(
          "[Donadores] Necesidad {} creada sin asignar: Logística no confirmó la asignación de {}"
              + " unidades",
          necesidad.getId(),
          aAsignar);
      return;
    }

    try {
      necesidad.registrarSatisfaccion(aAsignar);
      this.necesidadesRepository.save(necesidad);
      metricasService.incrementarNecesidadesSatisfechas();
    } catch (RuntimeException e) {
      // No debería pasar con las reglas de arriba; si pasa, Logística ya apartó las unidades y
      // hay que conciliarlo a mano, por eso el mensaje lo dice.
      log.warn(
          "[Donadores] Logística asignó {} unidades a la necesidad {} pero no se pudieron registrar"
              + " localmente: {}",
          aAsignar,
          necesidad.getId(),
          e.getMessage());
    }
  }

  @Override
  public QuejaDTO agregarQueja(QuejaDTO quejaDTO) throws NoSuchElementException {
    if (quejaDTO == null) {
      throw new RuntimeException("La queja no puede ser null");
    }

    // Una queja sin texto suma igual para el baneo, y nadie puede revisar después qué se reclamó.
    if (estaVacio(quejaDTO.descripcion())) {
      throw new RuntimeException("La queja tiene que tener una descripción");
    }

    // Se rechaza acá con un 400: si llegara al repositorio, el JPA revienta con un id nulo y el
    // en memoria responde "donador inexistente", y ninguno de los dos dice qué estuvo mal.
    if (estaVacio(quejaDTO.donadorID())) {
      throw new RuntimeException("La queja tiene que indicar el donador (donadorID)");
    }

    // El donacionID no se corrobora contra Donaciones a propósito: Donaciones es quien llama a
    // este endpoint y ya validó la donación; consultarlo de vuelta sería una dependencia circular.

    // Sin fecha, la queja se registra el día en que llega.
    LocalDate fecha = quejaDTO.fecha() != null ? quejaDTO.fecha() : LocalDate.now();

    String idGenerado = quejaDTO.id();
    if (idGenerado == null || idGenerado.isBlank()) {
      idGenerado = siguienteIdQueja();
    }

    final String idQueja = idGenerado;

    boolean yaExiste = todasLasQuejas().stream()
            .anyMatch(q -> q.getId() != null && q.getId().equals(idQueja));

    if (yaExiste) {
      throw new RuntimeException("Ya existe una queja con ese ID");
    }

    QuejaDTO dtoConId =
            new QuejaDTO(
                    idQueja,
                    quejaDTO.donacionID(),
                    quejaDTO.donadorID(),
                    fecha,
                    quejaDTO.descripcion());

    Queja queja = donadoresYEntidadesDataMapper.toQueja(dtoConId);

    // La queja se persiste en cascada a través del donador que la contiene.
    val donadorOptional = this.donadoresRepository.findById(quejaDTO.donadorID());
    if (donadorOptional.isEmpty()) {
      throw new DonadorNoEncontradoException(
              "No existe un donador con ID " + quejaDTO.donadorID() + " para asociar la queja");
    }

    Donador donador = donadorOptional.get();
    donador.agregarQueja(queja);
    this.donadoresRepository.save(donador);

    metricasService.incrementarQuejasRegistradas();
    // Dos eventos: la queja es un objeto propio en el modelo de lectura, y además le cambia al
    // donador la cantidad de quejas y quizás el estado (SOSPECHOSO con la quinta, BANEADO con la
    // décima).
    emitirEvento(e -> e.quejaRegistrada(queja));
    emitirEvento(e -> e.donadorModificado(donador));
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
    // Si no existe, removeById lanza y no se publica nada: no hay qué borrar en Consultas.
    this.necesidadesRepository.removeById(necesidadID);
    emitirEvento(e -> e.necesidadEliminada(necesidadID));
  }

  public NecesidadMaterialDTO modificarNecesidad(String necesidadID, NecesidadMaterialDTO dto) {
    val necesidadOptional = this.necesidadesRepository.findById(necesidadID);
    if (necesidadOptional.isEmpty()) {
      throw new NoSuchElementException("No existe una necesidad con ese ID");
    }
    if (dto == null) {
      throw new RuntimeException("Los datos de la necesidad no pueden ser null");
    }
    val necesidad = necesidadOptional.get();

    // Todas las validaciones van antes de tocar la entidad: con el repositorio en memoria el
    // objeto es el mismo que está guardado, y un rechazo a mitad de camino dejaría cambios a medias.
    // Objetivo y producto se validan solo si cambian: el bot reenvía la necesidad completa al
    // editar cualquier campo, y repetir lo que ya está guardado no tiene por qué fallar.
    if (dto.cantidadObjetivo() != null
        && !dto.cantidadObjetivo().equals(necesidad.getCantidadObjetivo())) {
      if (dto.cantidadObjetivo() <= 0) {
        metricasService.incrementarNecesidadesErrores();
        throw new RuntimeException("La cantidad objetivo de la necesidad debe ser mayor a 0");
      }
      // Lo entregado no se puede deshacer: un objetivo por debajo dejaría la necesidad
      // "satisfecha con sobrante", con más unidades cubiertas que pedidas. (Puede pasar igual
      // por una entrega mayor a lo que faltaba, pero eso no lo tiene que provocar una edición.)
      int cubierta = necesidad.getCantidadCubierta() != null ? necesidad.getCantidadCubierta() : 0;
      if (dto.cantidadObjetivo() < cubierta) {
        metricasService.incrementarNecesidadesErrores();
        throw new RuntimeException(
            "La cantidad objetivo (" + dto.cantidadObjetivo()
                + ") no puede ser menor a lo ya cubierto (" + cubierta + ")");
      }
    }
    // Para el producto además evita depender de que Donaciones esté despierto sin necesidad.
    if (dto.productoSolicitadoID() != null
        && !dto.productoSolicitadoID().equals(necesidad.getProductoSolicitadoID())) {
      validarProductoEnDonaciones(dto.productoSolicitadoID(), "modificar_necesidad");
    }

    if (dto.nivelDeUrgencia() != null) necesidad.setNivelDeUrgencia(dto.nivelDeUrgencia());
    if (dto.descripcion() != null) necesidad.setDescripcion(dto.descripcion());
    if (dto.cantidadObjetivo() != null) necesidad.setCantidadObjetivo(dto.cantidadObjetivo());
    if (dto.productoSolicitadoID() != null) {
      necesidad.setProductoSolicitadoID(dto.productoSolicitadoID());
    }
    if (dto.tipo() != null) necesidad.setTipo(dto.tipo());
    this.necesidadesRepository.save(necesidad);
    emitirEvento(e -> e.necesidadModificada(necesidad));
    return donadoresYEntidadesDataMapper.toNecesidadMaterialDTO(necesidad);
  }

  public EntidadBeneficaDTO editarEntidad(String entidadID, EntidadBeneficaDTO dto) {
    val entidadOptional = this.entidadesRepository.findById(entidadID);
    if (entidadOptional.isEmpty()) {
      throw new NoSuchElementException("No existe una entidad con ese ID");
    }
    val entidad = entidadOptional.get();
    if (dto.razonSocial() != null) entidad.setRazonSocial(dto.razonSocial());
    if (dto.domicilio() != null) entidad.setDomicilio(dto.domicilio());
    if (dto.telefono() != null) entidad.setTelefono(dto.telefono());
    if (dto.correo() != null) entidad.setCorreo(dto.correo());
    this.entidadesRepository.save(entidad);
    emitirEvento(e -> e.entidadModificada(entidad));
    return donadoresYEntidadesDataMapper.toEntidadBeneficaDTO(entidad);
  }
}
