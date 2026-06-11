package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

@Setter
@Getter
@Entity
@Table(name = "necesidades_materiales")
@NoArgsConstructor
public class NecesidadMaterial {

    @Id
    private String id;
    private String entidadID;
    private Integer nivelDeUrgencia;
    private String descripcion;
    private Integer cantidadObjetivo;
    private String productoSolicitadoID;
    private Integer cantidadCubierta;
    
    @Enumerated(EnumType.STRING)
    private TipoNecesidadMaterialEnum tipo;

    public NecesidadMaterial(
            String id,
            String entidadID,
            Integer nivelDeUrgencia,
            String descripcion,
            Integer cantidadObjetivo,
            String productoSolicitadoID,
            TipoNecesidadMaterialEnum tipo) {
        this.id = id;
        this.entidadID = entidadID;
        this.nivelDeUrgencia = nivelDeUrgencia;
        this.descripcion = descripcion;
        this.cantidadObjetivo = cantidadObjetivo;
        this.productoSolicitadoID = productoSolicitadoID;
        this.tipo = tipo != null ? tipo : TipoNecesidadMaterialEnum.EXTRAORDINARIA;
        this.cantidadCubierta = 0;
    }

    public Boolean estaSatisfecha() {
        return this.cantidadCubierta >= this.cantidadObjetivo;
    }

    public Integer cantidadFaltante() {
        return Math.max(0, this.cantidadObjetivo - this.cantidadCubierta);
    }

    public void registrarSatisfaccion(Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new RuntimeException("Cantidad inválida");
        }

        if (this.estaSatisfecha()) {
            throw new RuntimeException("La necesidad ya está satisfecha");
        }

        if (this.tipo == TipoNecesidadMaterialEnum.RECURRENTE && cantidad < this.cantidadObjetivo) {
            throw new RuntimeException("Una necesidad recurrente no puede satisfacerse parcialmente");
        }

        this.cantidadCubierta += cantidad;
    }

    public enum TipoNecesidad {
        EXTRAORDINARIA,
        RECURRENTE
    }

    public TipoNecesidadMaterialEnum getTipo() {
        return tipo;
    }
}

