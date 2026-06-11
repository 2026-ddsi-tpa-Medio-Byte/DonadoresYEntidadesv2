package ar.edu.utn.dds.k3003.catedra.incentivos;

import static org.mockito.Mockito.*;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.ClassFinder;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.CategoriaDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.InsigniaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.TipoMisionEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
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
@EnabledIf("ar.edu.utn.dds.k3003.catedra.incentivos.IncentivosTest#condicion")
public class IncentivosTest {

  FachadaIncentivos instancia;
  @Mock FachadaDonadoresYEntidades fachadaDonadoresYEntidades;

  InsigniaDTO insigniaEjemplo;
  MisionDTO misionEjemplo;
  DonadorDTO donadorEjemplo;

  @SneakyThrows
  @BeforeEach
  void setUp() {

    var clazz = ClassFinder.findClass();
    instancia = (FachadaIncentivos) clazz.getDeclaredConstructor().newInstance();

    instancia.setFachadaDonadoresYEntidades(fachadaDonadoresYEntidades);

    insigniaEjemplo = new InsigniaDTO(null, "insignia1", "descripcion1");
    InsigniaDTO retorno =
        instancia.agregarInsignia(new InsigniaDTO(null, "insignia", "descripcion"));

    misionEjemplo =
        new MisionDTO(
            null,
            "mision1",
            retorno.id(),
            CategoriaDonadorEnum.COLABORADOR,
            CategoriaDonadorEnum.TRANSFORMADOR,
            TipoMisionEnum.DONACIONES_EXITOSAS);
    donadorEjemplo =
        new DonadorDTO(
            "donador1",
            "donador1",
            "donador1",
            5,
            "donador1",
            "donador1",
            "donador1",
            EstadoDonadorEnum.VERIFICADO,
            "donador1");
  }

  static boolean condicion() {

    return FachadaIncentivos.class.isAssignableFrom(Fachada.class);
  }

  @Test
  void testAgregarInsignia() {
    InsigniaDTO retorno = instancia.agregarInsignia(insigniaEjemplo);

    Assertions.assertNotNull(retorno.id());
    Assertions.assertEquals(retorno.nombre(), insigniaEjemplo.nombre());
  }

  @Test
  void testAgregarInsigniaFallido() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.agregarInsignia(null);
        });

    InsigniaDTO insigniaRetorno = instancia.agregarInsignia(insigniaEjemplo);

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.agregarInsignia(insigniaRetorno);
        });
  }

  @Test
  void testAgregarMision() {

    MisionDTO retorno = instancia.agregarMision(misionEjemplo);

    Assertions.assertNotNull(retorno.id());
    Assertions.assertEquals(retorno.nombre(), misionEjemplo.nombre());
  }

  @Test
  void testAgregarMisionFallido() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.agregarMision(null);
        });

    MisionDTO misionRetorno = instancia.agregarMision(misionEjemplo);

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.agregarMision(misionRetorno);
        });
  }

  @Test
  void testAsignarInsigniaADonador() {

    when(fachadaDonadoresYEntidades.buscarDonadorPorID(donadorEjemplo.id()))
        .thenReturn(donadorEjemplo);

    InsigniaDTO insigniaRetorno = instancia.agregarInsignia(insigniaEjemplo);
    instancia.asignarInsigniaADonador(donadorEjemplo.id(), insigniaRetorno);

    List<InsigniaDTO> resultado = instancia.getInsigniasDeDonador(donadorEjemplo.id());

    Assertions.assertNotNull(resultado);
    Assertions.assertTrue(resultado.stream().anyMatch(i -> i.id().equals(insigniaRetorno.id())));

    verify(fachadaDonadoresYEntidades, times(1)).buscarDonadorPorID(donadorEjemplo.id());
  }

  @Test
  void testAsignarInsigniaNullADonador() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.asignarInsigniaADonador("1", null);
        });
  }

  @Test
  void testGetInsigniasDeDonadorInexistente() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.getInsigniasDeDonador("Inexistente");
        });
  }

  @Test
  void testAsignarMisionEnCursoDeDonador() {
    when(fachadaDonadoresYEntidades.buscarDonadorPorID(donadorEjemplo.id()))
        .thenReturn(donadorEjemplo);

    MisionDTO misionRetorno = instancia.agregarMision(misionEjemplo);
    instancia.asignarMisionADonador(donadorEjemplo.id(), misionRetorno);

    MisionDTO buscada = instancia.getMisionEnCursoDeDonador(donadorEjemplo.id());

    Assertions.assertNotNull(buscada);
    Assertions.assertEquals(buscada.id(), misionRetorno.id());

    verify(fachadaDonadoresYEntidades, times(1)).buscarDonadorPorID(donadorEjemplo.id());
  }

  @Test
  void testAsignarMisionNull() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.asignarMisionADonador(donadorEjemplo.id(), null);
        });
  }

  @Test
  void testGetMisionEnCursoDeDonadorFallido() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          instancia.getMisionEnCursoDeDonador("Inexistente");
        });
  }
}
