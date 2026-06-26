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

  @Mock private FachadaDonaciones fachadaDonaciones;

  @Mock private LogisticaStockClient logisticaStockClient;

  @BeforeEach
  void setUp() {
    fachada = new Fachada();
    fachada.setFachadaDonaciones(fachadaDonaciones);
    fachada.setLogisticaStockClient(logisticaStockClient);
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
            "entidad1",
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
            "entidad1",
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
            "entidad1",
            5,
            "necesidad1",
            5,
            "producto1",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    NecesidadMaterialDTO retorno = fachada.registrarNecesidad(dto);

    // objetivo 5, stock 3 → se solicita asignar 3
    verify(logisticaStockClient).solicitarAsignacion(retorno.id(), "producto1", 3);
  }
}
