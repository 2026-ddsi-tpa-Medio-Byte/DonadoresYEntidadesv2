package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.InsigniaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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

    @Override
    public List<InsigniaDTO> getInsigniasDeDonador(String donadorID) throws NoSuchElementException {
        String url = incentivosUrl + "/donadores/" + donadorID + "/insignias";
        try {
            InsigniaDTO[] insignias = restTemplate.getForObject(url, InsigniaDTO[].class);
            return insignias != null ? List.of(insignias) : List.of();
        } catch (Exception e) {
            throw new NoSuchElementException("No se encontraron insignias para el donador: " + donadorID);
        }
    }

    @Override
    public MisionDTO getMisionEnCursoDeDonador(String donadorID) throws NoSuchElementException {
        String url = incentivosUrl + "/donadores/" + donadorID + "/mision-actual";
        try {
            return restTemplate.getForObject(url, MisionDTO.class);
        } catch (Exception e) {
            throw new NoSuchElementException("No se encontró una misión en curso para el donador: " + donadorID);
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
