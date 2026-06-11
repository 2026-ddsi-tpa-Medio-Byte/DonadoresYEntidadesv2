package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorStatsDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.controllers.requests.CategoriaDonadorRequest;
import ar.edu.utn.dds.k3003.controllers.requests.EstadoDonadorRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/donadores")
public class DonadorController {

  private final Fachada fachada;

  public DonadorController(Fachada fachada) {
    this.fachada = fachada;
  }

  @Operation(summary = "Agregar un donador")
  @ApiResponses({
          @ApiResponse(responseCode = "201", description = "Donador creado correctamente"),
          @ApiResponse(responseCode = "400", description = "Solicitud inválida")
  })
  @PostMapping
  public ResponseEntity<DonadorDTO> postDonador(@RequestBody DonadorDTO donadorDTO) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(fachada.agregarDonador(donadorDTO));
  }

  @Operation(summary = "Obtener todos los donadores")
  @ApiResponse(responseCode = "200", description = "Lista de donadores")
  @GetMapping
  public ResponseEntity<List<DonadorDTO>> getDonadores() {
    return ResponseEntity.ok(fachada.buscarTodosLosDonadores());
  }

  @Operation(summary = "Buscar donador por ID")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Donador encontrado"),
          @ApiResponse(responseCode = "404", description = "Donador no encontrado")
  })
  @GetMapping("/{id}")
  public ResponseEntity<DonadorDTO> getDonadorByID(@PathVariable String id) {
    return ResponseEntity.ok(fachada.buscarDonadorPorID(id));
  }

  @Operation(summary = "Modificar estado del donador")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Estado actualizado"),
          @ApiResponse(responseCode = "400", description = "Estado inválido"),
          @ApiResponse(responseCode = "404", description = "Donador no encontrado")
  })
  @PatchMapping("/{id}/estado")
  public ResponseEntity<DonadorDTO> modificarEstado(
          @PathVariable String id,
          @RequestBody EstadoDonadorRequest request) {
    return ResponseEntity.ok(fachada.modificarEstado(id, request.estado()));
  }

  @Operation(summary = "Modificar categoría del donador")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Categoría actualizada"),
          @ApiResponse(responseCode = "400", description = "Categoría inválida"),
          @ApiResponse(responseCode = "404", description = "Donador no encontrado")
  })
  @PatchMapping("/{id}/categoria")
  public ResponseEntity<DonadorDTO> modificarCategoria(
          @PathVariable String id,
          @RequestBody CategoriaDonadorRequest request) {
    return ResponseEntity.ok(fachada.modifcarCategoria(id, request.categoria()));
  }

  @Operation(summary = "Verificar si un donador puede donar")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Resultado de validación"),
          @ApiResponse(responseCode = "404", description = "Donador no encontrado")
  })
  @GetMapping("/{id}/puede-donar")
  public ResponseEntity<Map<String, Boolean>> puedeDonar(@PathVariable String id) {
    return ResponseEntity.ok(Map.of("puedeDonar", fachada.puedeDonar(id)));
  }

  @Operation(summary = "Obtener estadísticas del donador")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Estadísticas del donador"),
          @ApiResponse(responseCode = "404", description = "Donador no encontrado")
  })
  @GetMapping("/{id}/estadisticas")
  public ResponseEntity<DonadorStatsDTO> estadisticasDonador(@PathVariable String id) {
    return ResponseEntity.ok(fachada.estadisticasDonador(id));
  }

  @Operation(summary = "Agregar una queja a un donador")
  @ApiResponses({
          @ApiResponse(responseCode = "201", description = "Queja creada correctamente"),
          @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
          @ApiResponse(responseCode = "404", description = "Donador no encontrado")
  })
  @PostMapping("/{id}/quejas")
  public ResponseEntity<QuejaDTO> agregarQueja(
          @PathVariable String id,
          @RequestBody QuejaDTO quejaDTO) {

    QuejaDTO dtoConDonador =
            new QuejaDTO(
                    quejaDTO.id(),
                    quejaDTO.donacionID(),
                    id,
                    quejaDTO.fecha(),
                    quejaDTO.descripcion());

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(fachada.agregarQueja(dtoConDonador));
  }

  @Operation(summary = "Obtener quejas de un donador")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Lista de quejas"),
          @ApiResponse(responseCode = "404", description = "Donador no encontrado")
  })
  @GetMapping("/{id}/quejas")
  public ResponseEntity<List<QuejaDTO>> obtenerQuejas(@PathVariable String id) {
    return ResponseEntity.ok(fachada.obtenerQuejasDe(id));
  }
}
