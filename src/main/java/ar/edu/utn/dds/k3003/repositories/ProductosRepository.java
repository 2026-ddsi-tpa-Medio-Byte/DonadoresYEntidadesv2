package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.ProductoSolicitado;
import java.util.List;
import java.util.Optional;

public interface ProductosRepository {
    ProductoSolicitado save(ProductoSolicitado productoSolicitado);
    Optional<ProductoSolicitado> findById(String id);
    void deleteById(String id);
    List<ProductoSolicitado> findAll();
}
