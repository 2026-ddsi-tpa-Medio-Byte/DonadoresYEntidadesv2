package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Entity
@Table(name = "donadores")
@NoArgsConstructor
public class Donador {

  @Id
  private String id;
  private String nombre;
  private String apellido;
  private Integer edad;
  private String email;
  private String nroDocumento;
  private String domicilio;
  
  @Enumerated(EnumType.STRING)
  private EstadoDonadorEnum estado;
  
  @Column
  private String categoria;
  
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private List<Queja> quejas;
  
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private List<CambioEstadoDonador> cambiosEstado;

  public Donador(
          String id,
          String nombre,
          String apellido,
          Integer edad,
          String email,
          String nroDocumento,
          String domicilio) {

    this.id = id;
    this.nombre = nombre;
    this.apellido = apellido;
    this.edad = edad;
    this.email = email;
    this.nroDocumento = nroDocumento;
    this.domicilio = domicilio;
    this.estado = EstadoDonadorEnum.VERIFICADO;
    this.categoria = "Colaborador";
    this.quejas = new ArrayList<>();
    this.cambiosEstado = new ArrayList<>();

    this.cambiosEstado.add(
            new CambioEstadoDonador(
                    null,
                    LocalDate.now(),
                    this.estado,
                    "Alta de donador"));
  }


  public Boolean puedeDonar() {
    return switch (this.estado) {
      case VERIFICADO -> true;
      case SOSPECHOSO -> new Random().nextBoolean();
      case BANEADO -> false;
    };
  }

  public void agregarQueja(Queja queja) {
    this.quejas.add(queja);
    this.actualizarEstadoSegunQuejas();
  }

  public Integer cantidadQuejas() {
    return this.quejas.size();
  }

  public void actualizarEstadoSegunQuejas() {
    if (this.cantidadQuejas() >= 10 && this.estado != EstadoDonadorEnum.BANEADO) {
      this.estado = EstadoDonadorEnum.BANEADO;
      this.cambiosEstado.add(
              new CambioEstadoDonador(
                      null,
                      LocalDate.now(),
                      EstadoDonadorEnum.BANEADO,
                      "Cambio automático por 10 quejas"));
    } else if (this.cantidadQuejas() >= 5 && this.estado == EstadoDonadorEnum.VERIFICADO) {
      this.estado = EstadoDonadorEnum.SOSPECHOSO;
      this.cambiosEstado.add(
              new CambioEstadoDonador(
                      null,
                      LocalDate.now(),
                      EstadoDonadorEnum.SOSPECHOSO,
                      "Cambio automático por 5 quejas"));
    }
  }

  public void modificarEstado(EstadoDonadorEnum nuevoEstado, String motivo) {
    this.estado = nuevoEstado;
    this.cambiosEstado.add(
            new CambioEstadoDonador(null, LocalDate.now(), nuevoEstado, motivo));
  }

  public void modificarCategoria(String categoria) {
    this.categoria = categoria;
  }

  /*
    public Donador(
        String nombre,
        String apellido,
        Integer edad,
        String email,
        String nroDocumento,
        String domicilio) {
      this.nombre = nombre;
      this.apellido = apellido;
      this.edad = edad;
      this.email = email;
      this.nroDocumento = nroDocumento;
      this.domicilio = domicilio;
      this.estado = EstadoDonadorEnum.VERIFICADO;
      this.categoria = "Ocasional";
    }
  */
  public List<Queja> getQuejas() {
    return quejas;
  }

  public List<CambioEstadoDonador> getCambiosEstado() {
    return cambiosEstado;
  }

  public void setQuejas(List<Queja> quejas) {
    this.quejas = quejas;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public String getApellido() {
    return apellido;
  }

  public void setApellido(String apellido) {
    this.apellido = apellido;
  }

  public Integer getEdad() {
    return edad;
  }

  public void setEdad(Integer edad) {
    this.edad = edad;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getNroDocumento() {
    return nroDocumento;
  }

  public void setNroDocumento(String nroDocumento) {
    this.nroDocumento = nroDocumento;
  }

  public String getDomicilio() {
    return domicilio;
  }

  public void setDomicilio(String domicilio) {
    this.domicilio = domicilio;
  }

  public EstadoDonadorEnum getEstado() {
    return estado;
  }

  public void setEstado(EstadoDonadorEnum estado) {
    this.estado = estado;
  }

  public String getCategoria() {
    return categoria;
  }

  public void setCategoria(String categoria) {
    this.categoria = categoria;
  }
}
