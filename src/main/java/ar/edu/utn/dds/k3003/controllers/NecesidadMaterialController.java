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
@Tag(name = "necesidades", description = "API de gestión de necesidades")
public class NecesidadMaterialController {

  private final Fachada fachada;

  public NecesidadMaterialController(Fachada fachada) {
    this.fachada = fachada;
  }

  @Operation(summary = "Agregar una necesidad Nueva")
  @PostMapping("/necesidades")
  public ResponseEntity<NecesidadMaterialDTO> agregarUnaNecesidad(@RequestBody NecesidadMaterialDTO necesidadMaterialDTO) {
    return ResponseEntity.status(HttpStatus.CREATED).body(fachada.registrarNecesidad(necesidadMaterialDTO));
  }

  @Operation(summary = "Buscar necesidades")
  @GetMapping("/necesidades")
  public ResponseEntity<List<NecesidadMaterialDTO>> buscarNecesidades() {
    return ResponseEntity.ok(
        fachada.buscarNecesidades());
  }

  @Operation(summary = "Buscar necesidad por ID")
  @GetMapping("/necesidades/{id}")
  public ResponseEntity<NecesidadMaterialDTO> buscarNecesidadPorID(@PathVariable String id) {
    return ResponseEntity.ok(fachada.buscarNecesidadPorID(id));
  }
  
  
                           
}
