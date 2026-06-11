package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.ProductoSolicitado;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryProductosRepo implements ProductosRepository {

    private final List<ProductoSolicitado> productos = new ArrayList<>();

    @Override
    public ProductoSolicitado save(ProductoSolicitado productoSolicitado) {
        this.deleteById(productoSolicitado.getId());
        this.productos.add(productoSolicitado);
        return productoSolicitado;
    }

    @Override
    public Optional<ProductoSolicitado> findById(String id) {
        return this.productos.stream()
                .filter(producto -> producto.getId().equals(id))
                .findFirst();
    }

    @Override
    public void deleteById(String id) {
        this.productos.removeIf(producto -> producto.getId().equals(id));
    }

    @Override
    public List<ProductoSolicitado> findAll() {
        return new ArrayList<>(this.productos);
    }
}