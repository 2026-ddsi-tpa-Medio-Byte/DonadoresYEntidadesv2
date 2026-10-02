package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.InsigniaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.NoSuchElementException;

@Component
public class IncentivosHttpClient implements FachadaIncentivos {

    @Value("${incentivos.url:http://localhost:8083}")
    private String incentivosUrl;

    private final RestTemplate restTemplate;

    @Autowired
    public IncentivosHttpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public InsigniaDTO agregarInsignia(InsigniaDTO insignia) {
        throw new UnsupportedOperationException("No utilizado por DonadoresYEntidades");
    }

    @Override
    public MisionDTO agregarMision(MisionDTO mision) {
        throw new UnsupportedOperationException("No utilizado por DonadoresYEntidades");
    }

    // Un 404 es una respuesta: Incentivos dice que el donador no tiene insignias o misión, y eso
    // se informa como lista vacía o null. Cualquier otro error (5xx, timeout, conexión rechazada)
    // es que Incentivos no pudo contestar, y sigue saliendo como excepción para que la fachada lo
    // cuente como llamada fallida. Antes los dos casos eran la misma NoSuchElementException, y un
    // donador sin misión sumaba un error a la alarma de integraciones en cada consulta.

    @Override
    public List<InsigniaDTO> getInsigniasDeDonador(String donadorID) throws NoSuchElementException {
        String url = incentivosUrl + "/donadores/" + donadorID + "/insignias";
        try {
            InsigniaDTO[] insignias = restTemplate.getForObject(url, InsigniaDTO[].class);
            return insignias != null ? List.of(insignias) : List.of();
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        } catch (RestClientException e) {
            throw new RuntimeException(
                    "Incentivos no respondió o falló al consultar las insignias del donador "
                            + donadorID + ": " + e.getMessage(),
                    e);
        }
    }

    @Override
    public MisionDTO getMisionEnCursoDeDonador(String donadorID) throws NoSuchElementException {
        String url = incentivosUrl + "/donadores/" + donadorID + "/mision-actual";
        try {
            return restTemplate.getForObject(url, MisionDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new RuntimeException(
                    "Incentivos no respondió o falló al consultar la misión en curso del donador "
                            + donadorID + ": " + e.getMessage(),
                    e);
        }
    }

    @Override
    public void asignarMisionADonador(String donadorID, MisionDTO misionDTO) throws NoSuchElementException {
        throw new UnsupportedOperationException("No utilizado por DonadoresYEntidades");
    }

    @Override
    public void asignarInsigniaADonador(String donadorID, InsigniaDTO insigniaDTO) throws NoSuchElementException {
        throw new UnsupportedOperationException("No utilizado por DonadoresYEntidades");
    }

    @Override
    public void procesarDonador(String donadorID) throws NoSuchElementException {
        throw new UnsupportedOperationException("No utilizado por DonadoresYEntidades");
    }

    @Override
    public void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones) {
        // No-op para cliente HTTP - la fachada remota maneja sus propias dependencias
    }

    @Override
    public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades) {
        // No-op para cliente HTTP - la fachada remota maneja sus propias dependencias
    }
}
