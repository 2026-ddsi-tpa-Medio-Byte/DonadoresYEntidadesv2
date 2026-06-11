package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "cambios_estado_donador")
public class CambioEstadoDonador {

    @Id
    private String id;
    
    @Column
    private LocalDate fecha;
    
    @Enumerated(EnumType.STRING)
    private EstadoDonadorEnum estado;
    
    @Column
    private String motivo;

    public CambioEstadoDonador() {}

    public CambioEstadoDonador(String id, LocalDate fecha, EstadoDonadorEnum estado, String motivo) {
        this.id = id;
        this.fecha = fecha;
        this.estado = estado;
        this.motivo = motivo;
    }
}
