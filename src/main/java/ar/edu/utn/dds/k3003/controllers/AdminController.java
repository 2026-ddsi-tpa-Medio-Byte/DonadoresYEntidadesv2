package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operaciones de administración para facilitar las pruebas y la corrección. */
@RestController
@Tag(name = "4 · Administración")
public class AdminController {

    private final Fachada fachada;

    public AdminController(Fachada fachada) {
        this.fachada = fachada;
    }

    @Operation(
            summary = "Vaciar la base de datos del módulo",
            description = "Borra donadores, entidades, necesidades y quejas. "
                    + "Al quedar vacía, la numeración de IDs vuelve a empezar en 1.")
    @ApiResponse(responseCode = "200", description = "Base de datos limpiada")
    @DeleteMapping("/reset")
    public ResponseEntity<String> resetBaseDeDatos() {
        fachada.resetBaseDeDatos();
        return ResponseEntity.ok("Base de datos limpiada");
    }
}
