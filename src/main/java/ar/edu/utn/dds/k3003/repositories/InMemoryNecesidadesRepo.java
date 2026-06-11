package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryNecesidadesRepo implements NecesidadesRepository {

    private final List<NecesidadMaterial> necesidades = new ArrayList<>();

    @Override
    public NecesidadMaterial save(NecesidadMaterial necesidadMaterial) {
        this.necesidades.removeIf(necesidad -> necesidad.getId().equals(necesidadMaterial.getId()));
        this.necesidades.add(necesidadMaterial);
        return necesidadMaterial;
    }

    @Override
    public Optional<NecesidadMaterial> findById(String id) {
        return this.necesidades.stream()
                .filter(necesidad -> necesidad.getId().equals(id))
                .findFirst();
    }

    @Override
    public NecesidadMaterial removeById(String id) {
        Optional<NecesidadMaterial> necesidad = this.findById(id);
        if (necesidad.isEmpty()) {
            throw new RuntimeException("No existe una necesidad con ese ID");
        }
        this.necesidades.remove(necesidad.get());
        return necesidad.get();
    }

    @Override
    public List<NecesidadMaterial> findAll() {
        return new ArrayList<>(this.necesidades);
    }
}