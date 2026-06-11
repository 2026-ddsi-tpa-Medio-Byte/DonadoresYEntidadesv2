package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/entidades")
public class EntidadController {

    private final Fachada fachada;

    public EntidadController(Fachada fachada) {
        this.fachada = fachada;
    }

    @Operation(summary = "Agregar entidad benéfica")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entidad creada"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida")
    })
    @PostMapping
    public ResponseEntity<EntidadBeneficaDTO> postEntidad(
            @RequestBody EntidadBeneficaDTO entidadBeneficaDTO) {

        EntidadBeneficaDTO entidadAgregada = fachada.agregarEntidad(entidadBeneficaDTO);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(entidadAgregada);
    }

    @Operation(summary = "Obtener todas las entidades")
    @ApiResponse(responseCode = "200", description = "Lista de entidades")
    @GetMapping
    public ResponseEntity<List<EntidadBeneficaDTO>> getEntidades() {
        return ResponseEntity.ok(fachada.buscarTodasLasEntidades());
    }

    @Operation(summary = "Buscar entidad por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entidad encontrada"),
            @ApiResponse(responseCode = "404", description = "Entidad no encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EntidadBeneficaDTO> getEntidadByID(@PathVariable String id) {
        EntidadBeneficaDTO entidad = fachada.buscarEntidadPorID(id);
        return ResponseEntity.ok(entidad);
    }
}