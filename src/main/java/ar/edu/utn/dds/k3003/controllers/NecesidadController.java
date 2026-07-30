package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.controllers.requests.SatisfaccionRequest;
import ar.edu.utn.dds.k3003.controllers.responses.NecesidadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/necesidades")
public class NecesidadController {

    private final Fachada fachada;

    public NecesidadController(Fachada fachada) {
        this.fachada = fachada;
    }

    @Operation(summary = "Registrar necesidad material")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Necesidad creada"),
            @ApiResponse(responseCode = "404", description = "Entidad no encontrada"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida")
    })
    @PostMapping
    public ResponseEntity<NecesidadMaterialDTO> registrarNecesidad(
            @RequestBody NecesidadMaterialDTO necesidadMaterialDTO) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fachada.registrarNecesidad(necesidadMaterialDTO));
    }

    @Operation(summary = "Obtener necesidades insatisfechas por producto",
            description = "Incluye cantidadActual (lo ya cubierto), que Logística usa para el matchmaking")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de necesidades"),
            @ApiResponse(responseCode = "404", description = "Necesidades no encontradas")
    })
    @GetMapping
    public ResponseEntity<List<NecesidadResponse>>
    obtenerNecesidadesInsatisfechasDeProducto(
            @RequestParam String productoID) {

        return ResponseEntity.ok(
                fachada.obtenerNecesidadesInsatisfechasDetalladasDe(productoID));
    }

    @Operation(summary = "Buscar necesidad por ID",
            description = "Incluye cantidadActual (lo ya cubierto)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Necesidad encontrada"),
            @ApiResponse(responseCode = "404", description = "Necesidad no encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NecesidadResponse> buscarNecesidadPorID(@PathVariable String id) {
        return ResponseEntity.ok(fachada.buscarNecesidadPorID(id));
    }

    @Operation(summary = "Satisfacer una necesidad")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Necesidad actualizada"),
            @ApiResponse(responseCode = "404", description = "Necesidad no encontrada"),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida")
    })
    @PostMapping("/{necesidadID}/satisfaccion")
    public ResponseEntity<NecesidadMaterialDTO> satisfacerNecesidad(
            @PathVariable String necesidadID,
            @RequestBody SatisfaccionRequest request) {

        return ResponseEntity.ok(
                fachada.satisfacerNecesidad(necesidadID, request.cantidad()));
    }
}