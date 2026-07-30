package ar.edu.utn.dds.k3003.controllers.responses;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;

/**
 * DTO de respuesta propio para las consultas de necesidades materiales.
 *
 * <p>Extiende la información del DTO de cátedra agregando {@code cantidadActual} (la cantidad ya
 * cubierta de la necesidad). Ese dato lo necesita el módulo de Logística para ejecutar el algoritmo
 * de matchmaking "Prioridad a sub-atendidos", que selecciona la necesidad más alejada de alcanzar su
 * cantidad objetivo.
 *
 * <p>Se define aquí, y no en el paquete {@code catedra}, porque ese paquete no puede modificarse.
 */
public record NecesidadResponse(
    String id,
    String entidadID,
    Integer nivelDeUrgencia,
    String descripcion,
    Integer cantidadObjetivo,
    Integer cantidadActual,
    String productoSolicitadoID,
    TipoNecesidadMaterialEnum tipo) {}
