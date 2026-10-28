package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.controllers.responses.CambioCategoriaResponse;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoEncontradoException;
import ar.edu.utn.dds.k3003.model.CambioCategoriaDonador;
import ar.edu.utn.dds.k3003.model.Donador;
import ar.edu.utn.dds.k3003.repositories.InMemoryDonadoresRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryEntidadesRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryNecesidadesRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El historial de categorías que pide la ficha del donador (consulta 1 de la Entrega 6).
 *
 * <p>Con repositorios en memoria; que además persista con JPA lo cubre
 * {@code EventosIntegrationTest}.
 */
class HistorialCategoriasTest {

  private static final Pattern FECHA_UTC =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");

  private Fachada fachada;

  @BeforeEach
  void setUp() {
    fachada =
        new Fachada(
            new InMemoryDonadoresRepo(), new InMemoryEntidadesRepo(), new InMemoryNecesidadesRepo(),
            new MetricasService(new SimpleMeterRegistry()));
  }

  private String alta(String categoria) {
    return fachada
        .agregarDonador(
            new DonadorDTO(null, "Ana", "Pérez", 30, "a@mail.com", "doc-" + System.nanoTime(),
                "Calle 1", null, categoria))
        .id();
  }

  private List<String> categorias(String donadorID) {
    return fachada.historialCategoriasDe(donadorID).stream()
        .map(CambioCategoriaResponse::categoria)
        .toList();
  }

  @Test
  @DisplayName("El alta sin categoría deja el historial con Colaborador, desde ese momento")
  void altaSinCategoria() {
    String id = alta(null);

    List<CambioCategoriaResponse> historial = fachada.historialCategoriasDe(id);

    assertEquals(1, historial.size());
    assertEquals("Colaborador", historial.get(0).categoria());
    assertTrue(FECHA_UTC.matcher(historial.get(0).desde()).matches(), historial.get(0).desde());
  }

  @Test
  @DisplayName("El alta con categoría arranca el historial con esa, sin pasar por Colaborador")
  void altaConCategoria() {
    String id = alta("Oro");

    assertEquals(List.of("Oro"), categorias(id));
    assertEquals("Oro", fachada.buscarDonadorPorID(id).categoria());
  }

  @Test
  @DisplayName("Cada cambio de categoría se agrega al final, de la más vieja a la actual")
  void cambios() {
    String id = alta(null);

    fachada.modifcarCategoria(id, "Plata");
    fachada.modifcarCategoria(id, "Oro");

    assertEquals(List.of("Colaborador", "Plata", "Oro"), categorias(id));
    List<String> desde =
        fachada.historialCategoriasDe(id).stream().map(CambioCategoriaResponse::desde).toList();
    assertTrue(desde.get(0).compareTo(desde.get(1)) <= 0, desde.toString());
    assertTrue(desde.get(1).compareTo(desde.get(2)) <= 0, desde.toString());
    assertEquals("Oro", fachada.buscarDonadorPorID(id).categoria());
  }

  @Test
  @DisplayName("Si la categoría no cambia, no se agrega una entrada")
  void mismaCategoria() {
    String id = alta(null);
    fachada.modifcarCategoria(id, "Plata");

    fachada.modifcarCategoria(id, "Plata");

    assertEquals(List.of("Colaborador", "Plata"), categorias(id));
  }

  @Test
  @DisplayName("Volver a una categoría anterior sí es un cambio y queda registrado")
  void volverAUnaAnterior() {
    String id = alta(null);
    fachada.modifcarCategoria(id, "Plata");

    fachada.modifcarCategoria(id, "Colaborador");

    assertEquals(List.of("Colaborador", "Plata", "Colaborador"), categorias(id));
  }

  @Test
  @DisplayName("El historial de un donador que no existe responde 404, no una lista vacía")
  void donadorInexistente() {
    assertThrows(DonadorNoEncontradoException.class, () -> fachada.historialCategoriasDe("999"));
  }

  @Test
  @DisplayName("En el dominio: el setter también pasa por el historial y no se puede saltear")
  void dominio() {
    Donador donador = new Donador("1", "Ana", "Pérez", 30, "a@mail.com", "1", "Calle 1");

    assertFalse(donador.modificarCategoria("Colaborador"));
    assertTrue(donador.modificarCategoria("Plata"));
    donador.setCategoria("Oro");

    assertEquals(
        List.of("Colaborador", "Plata", "Oro"),
        donador.getHistorialCategorias().stream().map(CambioCategoriaDonador::getCategoria).toList());
    assertThrows(
        UnsupportedOperationException.class,
        () -> donador.getHistorialCategorias().add(new CambioCategoriaDonador("Trucho", null)));
  }
}
