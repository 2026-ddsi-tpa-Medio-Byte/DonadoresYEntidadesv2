package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Component
public class DonacionesHttpClient implements FachadaDonaciones {

    @Value("${donaciones.url:http://localhost:8081}")
    private String donacionesUrl;

    private final RestTemplate restTemplate;

    @Autowired
    public DonacionesHttpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public DonacionDTO registrarDonacion(DonacionDTO donacionDTO) {
        String url = donacionesUrl + "/donaciones";
        return restTemplate.postForObject(url, donacionDTO, DonacionDTO.class);
    }

    @Override
    public DonacionDTO buscarDonacionPorID(String donacionID) throws NoSuchElementException {
        String url = donacionesUrl + "/donaciones/" + donacionID;
        try {
            return restTemplate.getForObject(url, DonacionDTO.class);
        } catch (Exception e) {
            throw new NoSuchElementException("No se encontró la donación con ID: " + donacionID);
        }
    }

    @Override
    public DonacionDTO cambiarEstadoDeDonacion(String donacionID, EstadoDonacionEnum estado)
            throws NoSuchElementException {
        String url = donacionesUrl + "/donaciones/" + donacionID + "/estado";
        try {
            // PATCH request for changing status
            return restTemplate.patchForObject(url, estado, DonacionDTO.class);
        } catch (Exception e) {
            throw new NoSuchElementException("No se pudo cambiar el estado de la donación con ID: " + donacionID);
        }
    }

    @Override
    public List<DonacionDTO> buscarPorDonadorYFechaInicio(String donadorID, LocalDate fecha)
            throws NoSuchElementException {
        String url = donacionesUrl + "/donaciones?donadorId=" + donadorID + "&fecha=" + fecha;
        try {
            DonacionDTO[] donaciones = restTemplate.getForObject(url, DonacionDTO[].class);
            return donaciones != null ? List.of(donaciones) : List.of();
        } catch (Exception e) {
            throw new NoSuchElementException("No se encontraron donaciones para el donador: " + donadorID);
        }
    }

    @Override
    public DonacionDTO registrarQuejaEnDonacion(String donacionID, String descripcion) {
        String url = donacionesUrl + "/donaciones/" + donacionID + "/quejas";
        return restTemplate.postForObject(url, descripcion, DonacionDTO.class);
    }

    @Override
    public ProductoDTO agregarProducto(ProductoDTO productoDTO) {
        String url = donacionesUrl + "/productos";
        return restTemplate.postForObject(url, productoDTO, ProductoDTO.class);
    }

    @Override
    public ProductoDTO buscarProductoPorID(String productoID) throws NoSuchElementException {
        String url = donacionesUrl + "/productos/" + productoID;
        try {
            return restTemplate.getForObject(url, ProductoDTO.class);
        } catch (Exception e) {
            throw new NoSuchElementException("No se encontró el producto con ID: " + productoID);
        }
    }

    @Override
    public IdentificadorDTO agregarIdentificador(IdentificadorDTO identificadorDTO) {
        String url = donacionesUrl + "/identificadores";
        return restTemplate.postForObject(url, identificadorDTO, IdentificadorDTO.class);
    }

    @Override
    public IdentificadorDTO buscarIdentificadorPorID(String identificadorID) throws NoSuchElementException {
        String url = donacionesUrl + "/identificadores/" + identificadorID;
        try {
            return restTemplate.getForObject(url, IdentificadorDTO.class);
        } catch (Exception e) {
            throw new NoSuchElementException("No se encontró el identificador con ID: " + identificadorID);
        }
    }

    @Override
    public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades) {
        // No-op para cliente HTTP - la fachada remota maneja sus propias dependencias
    }

    @Override
    public void setFachadaLogistica(FachadaLogistica fachadaLogistica) {
        // No-op para cliente HTTP - la fachada remota maneja sus propias dependencias
    }
}
