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
@Tag(name = "Entidades", description = "API de gestión de entidades")
public class EntidadBeneficaController {

  private final Fachada fachada;

  public EntidadBeneficaController(Fachada fachada) {
    this.fachada = fachada;
  }

  @Operation(summary = "Agregar una nueva entidad")
  @PostMapping("/entidades")
  public ResponseEntity<EntidadBeneficaDTO> agregarEntidad(@RequestBody EntidadBeneficaDTO entidadBeneficaDTO) {
    return ResponseEntity.status(HttpStatus.CREATED).body(fachada.agregarEntidad(entidadBeneficaDTO));
  }

  @Operation(summary = "Buscar entidades")
  @GetMapping("/entidades")
  public ResponseEntity<List<EntidadBeneficaDTO>> buscarEntidades() {
    return ResponseEntity.ok(
        fachada.buscarEntidades());
  }

  @Operation(summary = "Buscar entidad por ID")
  @GetMapping("/entidades/{id}")
  public ResponseEntity<EntidadBeneficaDTO> buscarEntidadPorID(@PathVariable String id) {
    return ResponseEntity.ok(fachada.buscarEntidadPorID(id));
  }
  
  @Operation(summary = "Agregar una necesidad en una entidad")
  @PostMapping("/entidades/{id}/necesidad")
  public ResponseEntity<NecesidadMaterialDTO> agregarNecesidad(
      @PathVariable String entidadID, @RequestBody NecesidadMaterialDTO necesidadMaterialDTO) {
    return ResponseEntity.ok(fachada.registrarNecesidadMaterial(necesidadMaterialDTO));
  }


  }
