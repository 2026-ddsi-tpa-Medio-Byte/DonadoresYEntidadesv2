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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column
    private LocalDate fecha;
    
    @Enumerated(EnumType.STRING)
    private EstadoDonadorEnum estado;
    
    @Column
    private String motivo;

    public CambioEstadoDonador() {}

    public CambioEstadoDonador( LocalDate fecha, EstadoDonadorEnum estado, String motivo) {
        this.fecha = fecha;
        this.estado = estado;
        this.motivo = motivo;
    }
}
