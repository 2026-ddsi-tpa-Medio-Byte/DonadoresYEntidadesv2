package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import java.util.List;

public interface EntidadesRepository {
    List<EntidadBenefica> findAll();
    EntidadBenefica removeById(String id);
}