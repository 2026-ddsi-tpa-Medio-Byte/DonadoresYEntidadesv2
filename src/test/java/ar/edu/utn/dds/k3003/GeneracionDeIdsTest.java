package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EntidadBeneficaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.model.Donador;
import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import ar.edu.utn.dds.k3003.repositories.DonadoresRepository;
import ar.edu.utn.dds.k3003.repositories.EntidadesRepository;
import ar.edu.utn.dds.k3003.repositories.InMemoryDonadoresRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryEntidadesRepo;
import ar.edu.utn.dds.k3003.repositories.InMemoryNecesidadesRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los IDs se generan a partir de lo persistido y no de contadores en memoria.
 *
 * <p>Regresión: al reiniciarse el servicio (deploy o cold start de Render), los contadores en RAM
 * volvían a cero mientras la base conservaba los registros, y el alta fallaba con "Ya existe una
 * entidad con ese ID". Estos tests simulan ese reinicio creando una Fachada nueva sobre
 * repositorios que ya contienen datos.
 */
class GeneracionDeIdsTest {

  /** Simula un reinicio: repositorios con datos previos y una Fachada recién construida. */
  private Fachada fachadaSobreRepositoriosConDatos(
          DonadoresRepository donadores, EntidadesRepository entidades) {
    return new Fachada(
            donadores, entidades, new InMemoryNecesidadesRepo(),
            new MetricasService(new SimpleMeterRegistry()));
  }

  @Test
  @DisplayName("Tras un reinicio con entidades ya persistidas, el alta no colisiona de ID")
  void altaDeEntidadTrasReinicio() {
    EntidadesRepository entidades = new InMemoryEntidadesRepo();
    entidades.save(new EntidadBenefica("1", "Comedor Uno", "Calle 1", "111", "uno@mail.com"));
    entidades.save(new EntidadBenefica("2", "Comedor Dos", "Calle 2", "222", "dos@mail.com"));

    Fachada fachada = fachadaSobreRepositoriosConDatos(new InMemoryDonadoresRepo(), entidades);

    EntidadBeneficaDTO nueva =
            new EntidadBeneficaDTO(
                    null, "Comedor Los Amigos", "Calle Falsa 123", "1156781234",
                    "loscomedores@mail.com");

    EntidadBeneficaDTO creada = assertDoesNotThrow(() -> fachada.agregarEntidad(nueva));

    assertEquals("3", creada.id());
  }

  @Test
  @DisplayName("Tras un reinicio con donadores ya persistidos, el alta continúa la numeración")
  void altaDeDonadorTrasReinicio() {
    DonadoresRepository donadores = new InMemoryDonadoresRepo();
    donadores.save(new Donador("1", "Ana", "Perez", 30, "a@x.com", "111", "Calle 1"));
    donadores.save(new Donador("2", "Luis", "Gomez", 40, "l@x.com", "222", "Calle 2"));

    Fachada fachada = fachadaSobreRepositoriosConDatos(donadores, new InMemoryEntidadesRepo());

    DonadorDTO nuevo =
            new DonadorDTO(
                    null, "Juan", "Lopez", 25, "j@x.com", "333", "Calle 3",
                    EstadoDonadorEnum.VERIFICADO, "Colaborador");

    DonadorDTO creado = assertDoesNotThrow(() -> fachada.agregarDonador(nuevo));

    assertEquals("3", creado.id());
  }

  @Test
  @DisplayName("Las quejas se leen de lo persistido y sobreviven a un reinicio")
  void quejasSobrevivenAlReinicio() {
    DonadoresRepository donadores = new InMemoryDonadoresRepo();
    Fachada fachadaOriginal =
            fachadaSobreRepositoriosConDatos(donadores, new InMemoryEntidadesRepo());

    DonadorDTO donador =
            fachadaOriginal.agregarDonador(
                    new DonadorDTO(
                            null, "Juan", "Lopez", 25, "j@x.com", "333", "Calle 3",
                            EstadoDonadorEnum.VERIFICADO, "Colaborador"));

    fachadaOriginal.agregarQueja(
            new QuejaDTO(null, "donacion1", donador.id(), null, "Llego en mal estado"));

    // Reinicio: nueva instancia de Fachada sobre el mismo repositorio persistido
    Fachada fachadaTrasReinicio =
            fachadaSobreRepositoriosConDatos(donadores, new InMemoryEntidadesRepo());

    var quejas = fachadaTrasReinicio.obtenerQuejasDe(donador.id());

    assertEquals(1, quejas.size(), "la queja debe seguir visible tras el reinicio");
    assertNotNull(quejas.get(0).id());
    assertEquals("Llego en mal estado", quejas.get(0).descripcion());
  }
}
