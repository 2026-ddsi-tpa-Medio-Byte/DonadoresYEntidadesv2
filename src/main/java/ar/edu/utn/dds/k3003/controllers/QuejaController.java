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
@Tag(name = "quejas", description = "API de gestión de quejas")
public class QuejaController {

  private final Fachada fachada;

  public QuejaController(Fachada fachada) {
    this.fachada = fachada;
  }

  @Operation(summary = "Agregar una queja Nueva")
  @PostMapping("/quejas")
  public ResponseEntity<QuejaDTO> agregarUnaQueja(@RequestBody QuejaDTO quejaDTO) {
    return ResponseEntity.status(HttpStatus.CREATED).body(fachada.agregarQueja(QuejaDTO));
  }

  @Operation(summary = "Buscar quejas")
  @GetMapping("/quejas")
  public ResponseEntity<List<QuejaDTO>> buscarQuejas() {
    return ResponseEntity.ok(
        fachada.buscarQuejas());
  }

  @Operation(summary = "Buscar queja por ID")
  @GetMapping("/quejas/{id}")
  public ResponseEntity<QuejaDTO> buscarQuejaPorID(@PathVariable String id) {
    return ResponseEntity.ok(fachada.buscarQuejaPorID(id));
  }
  
  
                           
  }
