package ar.edu.utn.dds.k3003.controllers;


import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "DonadoresStat", description = "API de gestión de donadoresStat")
public class DonadorStatController {

  private final Fachada fachada;

  public DonadorStatController(Fachada fachada) {
    this.fachada = fachada;
  }

  @Operation(summary = "Agregar un nuevo donadorStat")
  @PostMapping("/donadoresStat")
  public ResponseEntity<DonadorStatDTO> agregarDonadorStat(@RequestBody DonadorStatDTO donadorStatDTO) {
    return ResponseEntity.status(HttpStatus.CREATED).body(fachada.agregarDonadorStat(donadorStatDTO));
  }

  @Operation(summary = "Buscar donadoresStat")
  @GetMapping("/donadoresStat")
  public ResponseEntity<List<DonadorStatDTO>> buscarDonadoresStat() {
    return ResponseEntity.ok(
        fachada.buscarDonadoresStat());
  }

  @Operation(summary = "Buscar donadorstat por ID")
  @GetMapping("/donadoresStat/{id}")
  public ResponseEntity<DonadorStatDTO> buscarDonadorStatPorID(@PathVariable String id) {
    return ResponseEntity.ok(fachada.buscarDonadorStatsPorID(id));
  }
  
  @Operation(summary = "Agregar mision en un donadorStats")
  @PostMapping("/donadorStat/{id}/mision")
  public ResponseEntity<DonadorStatDTO> misionEnCurso(@PathVariable String id) {
    DonadorStatsDTO donadorStatsDTO = buscarDonadorStatsPorID(id);
    return ResponseEntity.ok(fachada.asignarMisionADonador(donadorStatsDTO.donadorID());
  }



  
  }
