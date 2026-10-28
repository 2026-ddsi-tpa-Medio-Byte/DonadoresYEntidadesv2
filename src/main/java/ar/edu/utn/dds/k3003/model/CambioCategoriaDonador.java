package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Desde cuándo un donador tiene una categoría.
 *
 * <p>La ficha del donador (consulta 1 de la Entrega 6) pide el historial de categorías, y hasta
 * ahora solo se guardaba la actual: Incentivos la pisa con su PATCH al completar una misión, y la
 * anterior se perdía. Sigue el mismo patrón que {@link CambioEstadoDonador}.
 *
 * <p>{@code desde} es un instante y no un día, a diferencia de los cambios de estado: dos misiones
 * completadas el mismo día dejan dos categorías, y con solo la fecha no se sabría cuál vino primero.
 */
@Getter
@Entity
@Table(name = "cambios_categoria_donador")
@NoArgsConstructor
public class CambioCategoriaDonador {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String categoria;

  @Column(nullable = false)
  private Instant desde;

  public CambioCategoriaDonador(String categoria, Instant desde) {
    this.categoria = categoria;
    this.desde = desde;
  }
}
