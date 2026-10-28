package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.eventos.PublicadorEventos;
import ar.edu.utn.dds.k3003.eventos.ResultadoRepublicacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones de administración para facilitar las pruebas y la corrección.
 *
 * <p>Los tags van en cada método y no en la clase, como en el resto de los controladores: con los
 * dos, springdoc junta ambos y un endpoint puede terminar listado en dos grupos.
 */
@RestController
public class AdminController {

    private final Fachada fachada;
    private final PublicadorEventos publicadorEventos;

    public AdminController(Fachada fachada, PublicadorEventos publicadorEventos) {
        this.fachada = fachada;
        this.publicadorEventos = publicadorEventos;
    }

    @Operation(
            summary = "Vaciar la base de datos del módulo",
            description = "Borra donadores, entidades, necesidades y quejas. "
                    + "Al quedar vacía, la numeración de IDs vuelve a empezar en 1. "
                    + "Publica MODULO_RESETEADO para que Consultas borre lo de este módulo.")
    @ApiResponse(responseCode = "200", description = "Base de datos limpiada")
    @Tag(name = "Administración")
    @DeleteMapping("/reset")
    public ResponseEntity<String> resetBaseDeDatos() {
        fachada.resetBaseDeDatos();
        return ResponseEntity.ok("Base de datos limpiada");
    }

    /**
     * La carga inicial del modelo de lectura y la forma de resincronizarlo si se perdieron eventos.
     *
     * <p>Se publica directo y no después de un commit: no cambia nada, solo vuelve a contar lo que
     * ya está guardado. Así se puede responder cuántos salieron de verdad.
     */
    @Operation(
            summary = "Republicar el estado actual como eventos",
            description = "Vuelve a publicar todos los donadores, entidades y necesidades "
                    + "(DONADOR_MODIFICADO, ENTIDAD_MODIFICADA, NECESIDAD_MODIFICADA) y todas las "
                    + "quejas (QUEJA_REGISTRADA), con ocurridoEn = ahora. Responde cuántos se "
                    + "publicaron y cuántos fallaron.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos publicados y fallidos"),
            @ApiResponse(responseCode = "503", description = "Publicador deshabilitado: falta RABBITMQ_URL")
    })
    @Tag(name = "Administración")
    @PostMapping("/admin/republicar-eventos")
    public ResponseEntity<?> republicarEventos() {
        // Con el publicador apagado, un "0 publicados" con 200 parecería una base vacía.
        if (!publicadorEventos.estaHabilitado()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("No se publicó nada: el publicador de eventos está deshabilitado"
                            + " (falta RABBITMQ_URL)");
        }
        ResultadoRepublicacion resultado =
                publicadorEventos.publicarTodos(fachada.eventosDelEstadoActual());
        return ResponseEntity.ok(resultado);
    }
}
