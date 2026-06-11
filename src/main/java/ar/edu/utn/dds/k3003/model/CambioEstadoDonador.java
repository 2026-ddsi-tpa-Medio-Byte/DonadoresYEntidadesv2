package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "cambios_estado_donador")
@NoArgsConstructor
public class CambioEstadoDonador {

    @Id
    private String id;
    public CambioEstadoDonador(String id, LocalDate fecha, EstadoDonadorEnum estado, String motivo) {
        // Constructor para compatibilidad con código existente que pasa id como String
        // El id será autogenerado por la BD
        this.fecha = fecha;
        this.estado = estado;
        this.motivo = motivo;
    }
}
