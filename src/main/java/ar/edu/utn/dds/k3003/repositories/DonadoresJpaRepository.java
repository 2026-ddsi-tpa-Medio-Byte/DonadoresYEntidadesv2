package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Donador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DonadoresJpaRepository extends JpaRepository<Donador, String>, DonadoresRepository {

    @Override
    default Optional<Donador> findById(String id) {
        return JpaRepository.super.findById(id);
    }

    @Override
    default Donador save(Donador donador) {
        return JpaRepository.super.save(donador);
    }

    @Override
    default Donador removeById(String id) {
        Donador d = findById(id)
            .orElseThrow(() -> new RuntimeException("No existe un donador con ese ID"));
        delete(d);
        return d;
    }
}
