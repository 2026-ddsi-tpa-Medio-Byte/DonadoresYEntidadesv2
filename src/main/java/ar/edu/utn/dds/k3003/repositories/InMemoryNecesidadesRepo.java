package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryNecesidadesRepo implements NecesidadesRepository {

    private final List<NecesidadMaterial> necesidades = new ArrayList<>();

    @Override
    public NecesidadMaterial save(NecesidadMaterial necesidadMaterial) {
        this.deleteById(necesidadMaterial.getId());
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
    public void deleteById(String id) {
        this.necesidades.removeIf(necesidad -> necesidad.getId().equals(id));
    }

    @Override
    public List<NecesidadMaterial> findAll() {
        return new ArrayList<>(this.necesidades);
    }
}