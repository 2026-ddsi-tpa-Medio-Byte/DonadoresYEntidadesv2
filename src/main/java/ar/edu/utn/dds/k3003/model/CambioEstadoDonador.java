package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class CambioEstadoDonador {

    private String id;
    private LocalDate fecha;
    private EstadoDonadorEnum estado;
    private String motivo;

    public CambioEstadoDonador(String id, LocalDate fecha, EstadoDonadorEnum estado, String motivo) {
        this.id = id;
        this.fecha = fecha;
        this.estado = estado;
        this.motivo = motivo;
    }

}
