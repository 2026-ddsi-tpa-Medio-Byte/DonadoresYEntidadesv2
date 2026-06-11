package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryEntidadesRepo implements EntidadesRepository {

    private final List<EntidadBenefica> entidades = new ArrayList<>();


    public EntidadBenefica save(EntidadBenefica entidadBenefica) {
        this.entidades.removeIf(entidad -> entidad.getId().equals(entidadBenefica.getId()));
        this.entidades.add(entidadBenefica);
        return entidadBenefica;
    }

    
    public Optional<EntidadBenefica> findById(String id) {
        return this.entidades.stream()
                .filter(entidad -> entidad.getId().equals(id))
                .findFirst();
    }

    
    public EntidadBenefica removeById(String id) {
        Optional<EntidadBenefica> entidad = this.findById(id);
        if (entidad.isEmpty()) {
            throw new RuntimeException("No existe una entidad con ese ID");
        }
        this.entidades.remove(entidad.get());
        return entidad.get();
    }

    
    public List<EntidadBenefica> findAll() {
        return new ArrayList<>(this.entidades);
    }
}