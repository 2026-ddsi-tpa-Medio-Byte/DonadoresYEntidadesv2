package ar.edu.utn.dds.k3003.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "quejas")
@NoArgsConstructor
public class Queja {

    @Id
    private String id;
    private String donacionID;
    private String donadorID;
    
    @Column
    private LocalDate fecha;
    private String descripcion;

    public Queja(String id, String donacionID, String donadorID, LocalDate fecha, String descripcion) {
        // Constructor para compatibilidad con código existente que pasa id como String
        // El id será autogenerado por la BD
        this.donacionID = donacionID;
        this.donadorID = donadorID;
        this.fecha = fecha;
        this.descripcion = descripcion;
    }
}