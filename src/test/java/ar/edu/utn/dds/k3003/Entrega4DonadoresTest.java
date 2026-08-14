package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.clients.LogisticaStockClient;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests de la Entrega 4 de Donadores: validación de producto contra Donaciones. */
@ExtendWith(MockitoExtension.class)
class Entrega4DonadoresTest {

  private Fachada fachada;

  /** La necesidad se cuelga de una entidad que existe: la fachada valida que el ID exista. */
  private String entidadID;

  @Mock private FachadaDonaciones fachadaDonaciones;

  @Mock private LogisticaStockClient logisticaStockClient;

  @BeforeEach
  void setUp() {
    fachada = new Fachada();
    fachada.setFachadaDonaciones(fachadaDonaciones);
    fachada.setLogisticaStockClient(logisticaStockClient);

    entidadID =
        fachada
            .agregarEntidad(
                new ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO(
                    null, "Comedor de prueba", "Calle 1", "1130000000", "comedor@mail.com"))
            .id();
  }

  @Test
  @DisplayName("Necesidad con producto válido se registra (valida contra Donaciones)")
  void necesidadConProductoValido() {
    when(fachadaDonaciones.buscarProductoPorID("producto1"))
        .thenReturn(
            new ProductoDTO("producto1", "Arroz", "Arroz blanco largo fino", "alimentos", "1"));

    NecesidadMaterialDTO dto =
        new NecesidadMaterialDTO(
            null,
            entidadID,
            5,
            "necesidad1",
            5,
            "producto1",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    NecesidadMaterialDTO retorno = fachada.registrarNecesidad(dto);

    assertNotNull(retorno.id());
    verify(fachadaDonaciones).buscarProductoPorID("producto1");
  }

  @Test
  @DisplayName("Necesidad con producto inexistente se rechaza")
  void necesidadConProductoInexistente() {
    when(fachadaDonaciones.buscarProductoPorID("noexiste"))
        .thenThrow(new NoSuchElementException("no existe"));

    NecesidadMaterialDTO dto =
        new NecesidadMaterialDTO(
            null,
            entidadID,
            5,
            "necesidad1",
            5,
            "noexiste",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    assertThrows(RuntimeException.class, () -> fachada.registrarNecesidad(dto));
  }

  @Test
  @DisplayName("Si Logística tiene stock, al crear la necesidad se solicita la asignación")
  void asignaStockAlCrearNecesidad() {
    when(fachadaDonaciones.buscarProductoPorID("producto1"))
        .thenReturn(
            new ProductoDTO("producto1", "Arroz", "Arroz blanco largo fino", "alimentos", "1"));
    when(logisticaStockClient.consultarStockDisponible("producto1")).thenReturn(3);

    NecesidadMaterialDTO dto =
        new NecesidadMaterialDTO(
            null,
            entidadID,
            5,
            "necesidad1",
            5,
            "producto1",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    NecesidadMaterialDTO retorno = fachada.registrarNecesidad(dto);

    // objetivo 5, stock 3 → se solicita asignar 3
    verify(logisticaStockClient).solicitarAsignacion(retorno.id(), "producto1", 3);
  }

  @Test
  @DisplayName("No se puede crear una necesidad colgada de una entidad que no existe")
  void necesidadConEntidadInexistente() {
    NecesidadMaterialDTO dto =
        new NecesidadMaterialDTO(
            null, "entidad-que-no-existe", 5, "necesidad huerfana", 10, null,
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    assertThrows(NoSuchElementException.class, () -> fachada.registrarNecesidad(dto));
  }

  @Test
  @DisplayName("La cantidad objetivo de una necesidad tiene que ser mayor a 0")
  void necesidadConCantidadObjetivoInvalida() {
    // Una necesidad de 0 nace satisfecha y le ensucia el matchmaking a Logística.
    for (Integer cantidad : java.util.Arrays.asList(0, -3, null)) {
      NecesidadMaterialDTO dto =
          new NecesidadMaterialDTO(
              null, entidadID, 5, "cantidad invalida", cantidad, null,
              TipoNecesidadMaterialEnum.EXTRAORDINARIA);

      RuntimeException e =
          assertThrows(RuntimeException.class, () -> fachada.registrarNecesidad(dto));
      org.junit.jupiter.api.Assertions.assertEquals(
          "La cantidad objetivo de la necesidad debe ser mayor a 0", e.getMessage());
    }
  }

  @Test
  @DisplayName("Buscar recursos inexistentes da 404, no 400")
  void recursosInexistentesSon404() {
    // Antes lanzaban RuntimeException genérica y el handler devolvía 400. Un recurso que no
    // existe tiene que responder 404 para que los otros módulos lo distingan de un pedido inválido.
    assertThrows(NoSuchElementException.class, () -> fachada.buscarEntidadPorID("999999"));
    assertThrows(NoSuchElementException.class, () -> fachada.estadisticasDonador("999999"));
    assertThrows(NoSuchElementException.class, () -> fachada.satisfacerNecesidad("999999", 1));
    assertThrows(NoSuchElementException.class, () -> fachada.modificarNecesidad("999999", null));
  }
}
