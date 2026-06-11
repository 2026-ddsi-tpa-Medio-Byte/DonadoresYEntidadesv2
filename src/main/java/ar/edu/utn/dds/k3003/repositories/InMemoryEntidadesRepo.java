package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryEntidadesRepo implements EntidadesRepository {

    private final List<EntidadBenefica> entidades = new ArrayList<>();

    @Override
    public EntidadBenefica save(EntidadBenefica entidadBenefica) {
        this.deleteById(entidadBenefica.getId());
        this.entidades.add(entidadBenefica);
        return entidadBenefica;
    }

    @Override
    public Optional<EntidadBenefica> findById(String id) {
        return this.entidades.stream()
                .filter(entidad -> entidad.getId().equals(id))
                .findFirst();
    }

    @Override
    public void deleteById(String id) {
        this.entidades.removeIf(entidad -> entidad.getId().equals(id));
    }

    @Override
    public List<EntidadBenefica> findAll() {
        return new ArrayList<>(this.entidades);
    }
}