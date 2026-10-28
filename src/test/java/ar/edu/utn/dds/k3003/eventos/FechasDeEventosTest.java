package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Las fechas que salen en los eventos: siempre UTC, con milisegundos y {@code Z}. */
class FechasDeEventosTest {

  private static final Instant LAS_18_32 = Instant.parse("2026-10-26T18:32:10Z");

  @Test
  @DisplayName("ocurridoEn sale en UTC con milisegundos aunque sean cero")
  void ocurridoEnConMilisegundos() {
    EventosDonadores eventos =
        new EventosDonadores(e -> {}, Clock.fixed(LAS_18_32, ZoneOffset.UTC));

    assertEquals(
        "2026-10-26T18:32:10.000Z", eventos.moduloReseteado().sobre().ocurridoEn());
  }

  @Test
  @DisplayName("Dos eventos en el mismo milisegundo no empatan: el segundo sale un milisegundo después")
  void ocurridoEnEstrictamenteCreciente() {
    // Con el reloj parado, como si los tres pasaran en el mismo milisegundo.
    EventosDonadores eventos =
        new EventosDonadores(e -> {}, Clock.fixed(LAS_18_32, ZoneOffset.UTC));

    List<String> horas = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      horas.add(eventos.necesidadEliminada("1").sobre().ocurridoEn());
    }

    assertEquals(
        List.of(
            "2026-10-26T18:32:10.000Z", "2026-10-26T18:32:10.001Z", "2026-10-26T18:32:10.002Z"),
        horas);
  }

  @Test
  @DisplayName("Un instante con nanosegundos se escribe truncado a milisegundos")
  void truncaAMilisegundos() {
    assertEquals(
        "2026-10-26T18:32:10.123Z",
        FechasUtc.formatear(Instant.parse("2026-10-26T18:32:10.123456789Z")));
  }

  @Test
  @DisplayName("Una fecha sin hora es el comienzo de ese día en Argentina, expresado en UTC")
  void fechaSinHora() {
    // 00:00 en Buenos Aires son las 03:00 UTC: el día es el mismo leído en UTC o en hora local.
    assertEquals("2026-10-26T03:00:00.000Z", FechasUtc.inicioDelDia(LocalDate.of(2026, 10, 26)));
    assertEquals("2026-01-01T03:00:00.000Z", FechasUtc.inicioDelDia(LocalDate.of(2026, 1, 1)));
  }

  @Test
  @DisplayName("Sin fecha no se inventa una: queda null")
  void sinFecha() {
    assertNull(FechasUtc.formatear(null));
    assertNull(FechasUtc.inicioDelDia(null));
  }
}
