package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Entity
@Table(name = "donadores")
@NoArgsConstructor
public class Donador {

  /** La que recibe un donador si el alta no trae otra. */
  public static final String CATEGORIA_INICIAL = "Colaborador";

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

  // Con columna en la tabla hija y no con tabla intermedia: es una relación uno a muchos y no
  // hace falta una tercera tabla. El orden lo fija la base, porque una lista sin orden puede volver
  // mezclada y el historial dejaría de contar la secuencia real.
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "donador_id")
  @OrderBy("desde ASC, id ASC")
  private List<CambioCategoriaDonador> historialCategorias;

  public Donador(
          String id,
          String nombre,
          String apellido,
          Integer edad,
          String email,
          String nroDocumento,
          String domicilio) {
    this(id, nombre, apellido, edad, email, nroDocumento, domicilio, CATEGORIA_INICIAL);
  }

  /**
   * Alta con la categoría con la que entra el donador.
   *
   * <p>La categoría inicial se recibe acá y no se pisa después con el setter: si no, el historial
   * arrancaría con "Colaborador" aunque el donador nunca lo haya sido.
   */
  public Donador(
          String id,
          String nombre,
          String apellido,
          Integer edad,
          String email,
          String nroDocumento,
          String domicilio,
          String categoria) {

    this.id = id;
    this.nombre = nombre;
    this.apellido = apellido;
    this.edad = edad;
    this.email = email;
    this.nroDocumento = nroDocumento;
    this.domicilio = domicilio;
    this.estado = EstadoDonadorEnum.VERIFICADO;
    this.categoria = categoria != null ? categoria : CATEGORIA_INICIAL;
    this.quejas = new ArrayList<>();
    this.cambiosEstado = new ArrayList<>();
    this.historialCategorias = new ArrayList<>();

    this.cambiosEstado.add(
            new CambioEstadoDonador(
                    null,
                    LocalDate.now(),
                    this.estado,
                    "Alta de donador"));
    this.historialCategorias.add(new CambioCategoriaDonador(this.categoria, ahora()));
  }

  /** Milisegundos: es lo que guarda la base y lo que muestran los eventos, así no difieren. */
  private static Instant ahora() {
    return Instant.now().truncatedTo(ChronoUnit.MILLIS);
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

  /**
   * Cambia la categoría y deja el cambio en el historial.
   *
   * <p>Si es la misma que ya tiene, no se registra nada: Incentivos puede mandar el PATCH con la
   * categoría vigente, y una entrada repetida haría parecer que la ganó dos veces.
   *
   * @return si la categoría cambió
   */
  public boolean modificarCategoria(String nuevaCategoria) {
    if (Objects.equals(this.categoria, nuevaCategoria)) {
      return false;
    }
    this.categoria = nuevaCategoria;
    historial().add(new CambioCategoriaDonador(nuevaCategoria, ahora()));
    return true;
  }

  /**
   * Las categorías que tuvo, de la más vieja a la actual.
   *
   * <p>Un donador dado de alta antes de la Entrega 6 lo tiene vacío hasta su primer cambio: de lo
   * anterior no quedó registro y no se inventa una fecha.
   */
  public List<CambioCategoriaDonador> getHistorialCategorias() {
    return Collections.unmodifiableList(historial());
  }

  private List<CambioCategoriaDonador> historial() {
    if (this.historialCategorias == null) {
      this.historialCategorias = new ArrayList<>();
    }
    return this.historialCategorias;
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

  /** Pasa por el dominio para que ningún cambio de categoría quede fuera del historial. */
  public void setCategoria(String categoria) {
    modificarCategoria(categoria);
  }
}
