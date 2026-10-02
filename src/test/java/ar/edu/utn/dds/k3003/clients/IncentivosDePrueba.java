package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.logging.TrazabilidadFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Un Incentivos falso que escucha en un puerto local, para probar el cliente con HTTP de verdad.
 *
 * <p>Con {@code MockRestServiceServer} no sirve: reemplaza la capa de red, así que un timeout nunca
 * llega a ocurrir. Lo que se vio en producción fue un Incentivos que acepta la conexión y no
 * contesta, y para reproducirlo hace falta un servidor real que haga exactamente eso.
 */
public final class IncentivosDePrueba implements AutoCloseable {

  /**
   * Cuánto espera, como mucho, un pedido que no se contesta. Es un tope para no dejar hilos vivos
   * si algo sale mal: el cierre los suelta antes.
   */
  private static final long ESPERA_MAXIMA_SEGUNDOS = 60;

  private final HttpServer servidor;
  // Con el ejecutor por defecto los pedidos se atienden de a uno: el primero que se cuelga
  // bloquearía al segundo, y el test mediría otra cosa.
  private final ExecutorService hilos = Executors.newCachedThreadPool();
  private final CountDownLatch soltarPedidos = new CountDownLatch(1);
  private final List<String> rutasPedidas = new CopyOnWriteArrayList<>();
  private final List<String> traceIdsRecibidos = new CopyOnWriteArrayList<>();
  private final String respuesta;

  private IncentivosDePrueba(String respuesta) {
    this.respuesta = respuesta;
    try {
      servidor =
          HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo levantar el Incentivos de prueba", e);
    }
    servidor.setExecutor(hilos);
    servidor.createContext("/", this::atender);
    servidor.start();
  }

  /** Acepta la conexión y no contesta hasta que se lo cierra: como Incentivos colgado. */
  public static IncentivosDePrueba queNoContesta() {
    return new IncentivosDePrueba(null);
  }

  /** Contesta al instante con un 200 y el JSON indicado. */
  public static IncentivosDePrueba queContesta(String json) {
    return new IncentivosDePrueba(json);
  }

  public String url() {
    return "http://"
        + servidor.getAddress().getHostString()
        + ":"
        + servidor.getAddress().getPort();
  }

  /** Las rutas que llegaron, en orden de llegada. */
  public List<String> rutasPedidas() {
    return List.copyOf(rutasPedidas);
  }

  /** El header {@code X-Trace-Id} de cada pedido, o cadena vacía si no vino. */
  public List<String> traceIdsRecibidos() {
    return List.copyOf(traceIdsRecibidos);
  }

  private void atender(HttpExchange pedido) {
    rutasPedidas.add(pedido.getRequestURI().getPath());
    String traceId = pedido.getRequestHeaders().getFirst(TrazabilidadFilter.HEADER_TRACE);
    traceIdsRecibidos.add(traceId == null ? "" : traceId);
    try (pedido) {
      if (respuesta == null) {
        esperarAQueLoCierren();
        // Para entonces el cliente ya se fue; si la conexión sigue abierta, que no quede colgada.
        pedido.sendResponseHeaders(503, -1);
        return;
      }
      byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
      pedido.getResponseHeaders().add("Content-Type", "application/json");
      pedido.sendResponseHeaders(200, cuerpo.length);
      try (OutputStream salida = pedido.getResponseBody()) {
        salida.write(cuerpo);
      }
    } catch (IOException e) {
      // El cliente cortó por timeout y cerró la conexión: es justo lo que se está probando.
    }
  }

  private void esperarAQueLoCierren() {
    try {
      soltarPedidos.await(ESPERA_MAXIMA_SEGUNDOS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    soltarPedidos.countDown();
    servidor.stop(0);
    hilos.shutdownNow();
  }
}
