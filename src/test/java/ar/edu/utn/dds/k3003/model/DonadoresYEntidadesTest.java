package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.repositories.DonadoresYEntidadesDataMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class DonadoresYEntidadesTest {

  Fachada instancia;

  @SneakyThrows
  @BeforeEach
  void setUp() {
    instancia = new Fachada();
  }

  @Test
  void testSiempreTrue() {
    Assertions.assertTrue(true);
  }

  @Test
  void testSiempreEquals() {
    Assertions.assertEquals(1, 1);
  }

  @Test
  void donadorPasaASospechosoConCincoQuejas() {
    Donador donador = new Donador(null,"Juan", "Perez", 25, "a@a.com", "123", "Calle 1");

    for (int i = 1; i <= 5; i++) {
      donador.agregarQueja(new Queja(String.valueOf(i), "donacion" + i, "1", null, "queja"));
    }

    Assertions.assertEquals(EstadoDonadorEnum.SOSPECHOSO, donador.getEstado());
  }

  @Test
  void donadorVerificadoPuedeDonar() {
    Donador donador = new Donador(null,"Juan", "Perez", 25, "a@a.com", "123", "Calle 1");

    Assertions.assertTrue(donador.puedeDonar());
  }

  @Test
  void necesidadNuevaNoEstaSatisfecha() {
    NecesidadMaterial necesidad = new NecesidadMaterial("1", "entidad1", 5, "desc", 10, "prod1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    Assertions.assertFalse(necesidad.estaSatisfecha());
  }

  @Test
  void necesidadSeSatisfaceParcialmente() {
    NecesidadMaterial necesidad = new NecesidadMaterial("1", "entidad1", 5, "desc", 10, "prod1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    necesidad.registrarSatisfaccion(4);

    Assertions.assertEquals(4, necesidad.getCantidadCubierta());
    Assertions.assertFalse(necesidad.estaSatisfecha());
  }

  @Test
  void registrarSatisfaccionInvalidaFalla() {
    NecesidadMaterial necesidad = new NecesidadMaterial("1", "entidad1", 5, "desc", 10, "prod1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    Assertions.assertThrows(RuntimeException.class, () -> necesidad.registrarSatisfaccion(0));
    Assertions.assertThrows(RuntimeException.class, () -> necesidad.registrarSatisfaccion(-1));
  }

  @Test
  void mapperDeDonadorConvierteCorrectamente() {
    DonadoresYEntidadesDataMapper mapper = new DonadoresYEntidadesDataMapper();

    DonadorDTO dto = new DonadorDTO(
            "1", "Juan", "Perez", 20, "mail", "123", "dom",
            EstadoDonadorEnum.VERIFICADO, "Colaborador"
    );

    Donador donador = mapper.toDonador(dto);

    Assertions.assertEquals("1", donador.getId());
    Assertions.assertEquals("Juan", donador.getNombre());
    Assertions.assertEquals("Colaborador", donador.getCategoria());
  }
  @Test
  void satisfacerNecesidadLaQuitaDeLasInsatisfechas() {
    Fachada fachada = new Fachada();

    NecesidadMaterialDTO necesidad =
            new NecesidadMaterialDTO(null, "entidad1", 5, "desc", 5, "prod1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);

    NecesidadMaterialDTO guardada = fachada.registrarNecesidad(necesidad);
    fachada.satisfacerNecesidad(guardada.id(), 5);

    List<NecesidadMaterialDTO> resultado = fachada.obtenerNecesidadesInsatisfechasDe("prod1");

    Assertions.assertTrue(resultado.isEmpty());
  }
}
