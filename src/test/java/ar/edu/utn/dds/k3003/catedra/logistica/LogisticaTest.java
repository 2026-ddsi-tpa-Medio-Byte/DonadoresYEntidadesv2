package ar.edu.utn.dds.k3003.catedra.logistica;

import static org.mockito.Mockito.*;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.ClassFinder;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@EnabledIf("ar.edu.utn.dds.k3003.catedra.logistica.LogisticaTest#condicion")
public class LogisticaTest {

  FachadaLogistica instancia;
  @Mock FachadaDonadoresYEntidades fachadaDonadoresYEntidades;
  @Mock FachadaDonaciones fachadaDonaciones;

  DepositoDTO depositoEjemplo;
  NecesidadMaterialDTO necesidadDeEjemplo;
  PaqueteDTO paqueteEjemplo;

  @SneakyThrows
  @BeforeEach
  void setUp() {

    var clazz = ClassFinder.findClass();
    instancia = (FachadaLogistica) clazz.getDeclaredConstructor().newInstance();

    instancia.setFachadaDonadoresYEntidades(fachadaDonadoresYEntidades);
    instancia.setFachadaDonaciones(fachadaDonaciones);

    depositoEjemplo = new DepositoDTO(null, null, "deposito1", "direccion1", 1000, null);
    necesidadDeEjemplo =
        new NecesidadMaterialDTO(
            null,
            "entidad1",
            5,
            "descripcion1",
            10,
            "producto1",
            TipoNecesidadMaterialEnum.EXTRAORDINARIA);
    paqueteEjemplo = new PaqueteDTO("paquete1", "donacion1", "producto1", 10);
  }

  static boolean condicion() {

    return FachadaLogistica.class.isAssignableFrom(Fachada.class);
  }

  @Test
  void testAgregarDeposito() {

    DepositoDTO retorno = instancia.agregarDeposito(depositoEjemplo);
    instancia.setAlgoritmoMM(retorno.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    Assertions.assertNotNull(retorno.id());
    Assertions.assertEquals(retorno.nombre(), depositoEjemplo.nombre());
  }

  @Test
  void testAgregarDepositoFallido() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.agregarDeposito(null);
        });

    DepositoDTO retorno = instancia.agregarDeposito(depositoEjemplo);
    instancia.setAlgoritmoMM(retorno.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.agregarDeposito(retorno);
        });
  }

  @Test
  void testBuscarDepositoPorID() {
    DepositoDTO retorno = instancia.agregarDeposito(depositoEjemplo);
    instancia.setAlgoritmoMM(retorno.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    DepositoDTO buscado = instancia.buscarDepositoPorID(retorno.id());

    Assertions.assertNotNull(buscado);
    Assertions.assertEquals(retorno.id(), buscado.id());
  }

  @Test
  void testBuscarDepositoPorIDFallido() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.buscarDepositoPorID("Inexistente");
        });
  }

  @Test
  void testGestionarDonacion() {

    when(fachadaDonadoresYEntidades.obtenerNecesidadesInsatisfechasDe("producto1"))
        .thenReturn(
            List.of(
                new NecesidadMaterialDTO(
                    "necesidad1",
                    "entidad1",
                    5,
                    "descripcion1",
                    5,
                    "producto1",
                    TipoNecesidadMaterialEnum.EXTRAORDINARIA)));

    DepositoDTO depositoRetorno = instancia.agregarDeposito(depositoEjemplo);
    instancia.setAlgoritmoMM(depositoRetorno.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    DepositoDTO actualizado =
        instancia.gestionarDonacion(depositoRetorno.id(), "donacion1", "producto1", 10);

    Assertions.assertNotNull(actualizado);
    Assertions.assertEquals(actualizado.id(), depositoRetorno.id());

    verify(fachadaDonadoresYEntidades, times(1)).obtenerNecesidadesInsatisfechasDe("producto1");
  }

  @Test
  void testGestionarDonacionFallido() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.gestionarDonacion("Inexistente", "donacion1", "producto1", 10);
        });

    DepositoDTO depositoRetorno = instancia.agregarDeposito(depositoEjemplo);
    instancia.setAlgoritmoMM(depositoRetorno.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.gestionarDonacion(depositoRetorno.id(), "donacion1", "producto1", -1);
        });

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.gestionarDonacion(depositoRetorno.id(), "donacion1", "producto1", 0);
        });
  }

  @Test
  void testEjecutarMatchmaking() {

    List<NecesidadMaterialDTO> necesidades =
        List.of(
            new NecesidadMaterialDTO(
                "necesidad1",
                "entidad1",
                5,
                "descripcion1",
                5,
                "producto1",
                TipoNecesidadMaterialEnum.EXTRAORDINARIA));

    DepositoDTO depositoRetorno = instancia.agregarDeposito(depositoEjemplo);
    instancia.setAlgoritmoMM(depositoRetorno.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    AsignacionDTO asignacion =
        instancia.ejecutarMatchmaking(depositoRetorno.id(), paqueteEjemplo, necesidades);

    Assertions.assertNotNull(asignacion);
    Assertions.assertEquals(paqueteEjemplo.id(), asignacion.paqueteID());
    Assertions.assertEquals(necesidades.getFirst().id(), asignacion.necesidadID());
  }

  @Test
  void testEjecutarMatchmakingFallido() {

    List<NecesidadMaterialDTO> necesidades =
        List.of(
            new NecesidadMaterialDTO(
                "necesidad1",
                "entidad1",
                5,
                "descripcion1",
                5,
                "producto1",
                TipoNecesidadMaterialEnum.EXTRAORDINARIA));

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.ejecutarMatchmaking(null, null, necesidades);
        });
  }

  @Test
  void testReportarEntrega() {

    when(fachadaDonaciones.cambiarEstadoDeDonacion(
            paqueteEjemplo.donacionID(), EstadoDonacionEnum.ACEPTADA))
        .thenReturn(
            new DonacionDTO(
                paqueteEjemplo.donacionID(),
                "donador1",
                "deposito1",
                "descripcion1",
                paqueteEjemplo.producto(),
                paqueteEjemplo.cantidad(),
                EstadoDonacionEnum.ACEPTADA));
    when(fachadaDonadoresYEntidades.satisfacerNecesidad("necesidad1", paqueteEjemplo.cantidad()))
        .thenReturn(necesidadDeEjemplo);

    DepositoDTO depositoRetorno = instancia.agregarDeposito(depositoEjemplo);
    instancia.setAlgoritmoMM(depositoRetorno.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    AsignacionDTO asignacionDTO =
        instancia.ejecutarMatchmaking(
            depositoRetorno.id(), paqueteEjemplo, List.of(necesidadDeEjemplo));

    instancia.reportarEntrega(paqueteEjemplo);

    Assertions.assertEquals(
        EstadoAsginacionEnum.COMPLETADA,
        instancia.buscarAsignacionPorPaqueteID(paqueteEjemplo.id()).estado());

    verify(fachadaDonadoresYEntidades, times(1))
        .satisfacerNecesidad("necesidad1", paqueteEjemplo.cantidad());
    verify(fachadaDonaciones, times(1))
        .cambiarEstadoDeDonacion(paqueteEjemplo.donacionID(), EstadoDonacionEnum.ACEPTADA);
  }

  @Test
  void testReportarEntregaFallido() {

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.reportarEntrega(null);
        });

    when(fachadaDonaciones.cambiarEstadoDeDonacion(
            paqueteEjemplo.donacionID(), EstadoDonacionEnum.ACEPTADA))
        .thenThrow(new RuntimeException());
    when(fachadaDonadoresYEntidades.satisfacerNecesidad("necesidad1", paqueteEjemplo.cantidad()))
        .thenReturn(necesidadDeEjemplo);

    DepositoDTO depositoRetorno = instancia.agregarDeposito(depositoEjemplo);
    instancia.setAlgoritmoMM(depositoRetorno.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    AsignacionDTO asignacionDTO =
        instancia.ejecutarMatchmaking(
            depositoRetorno.id(), paqueteEjemplo, List.of(necesidadDeEjemplo));

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.reportarEntrega(paqueteEjemplo);
        });

    verify(fachadaDonadoresYEntidades, times(1))
        .satisfacerNecesidad("necesidad1", paqueteEjemplo.cantidad());
    verify(fachadaDonaciones, times(1))
        .cambiarEstadoDeDonacion(paqueteEjemplo.donacionID(), EstadoDonacionEnum.ACEPTADA);
  }
}
