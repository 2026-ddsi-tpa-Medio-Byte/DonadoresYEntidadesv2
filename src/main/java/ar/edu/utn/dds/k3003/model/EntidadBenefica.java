package ar.edu.utn.dds.k3003.model;

public class EntidadBenefica {

    private String id;
    private String razonSocial;
    private String domicilio;
    private String telefono;
    private String correo;

    public EntidadBenefica(
            String id,
            String razonSocial,
            String domicilio,
            String telefono,
            String correo) {
        this.id = id;
        this.razonSocial = razonSocial;
        this.domicilio = domicilio;
        this.telefono = telefono;
        this.correo = correo;
    }

    public String getId() {
        return id;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public String getDomicilio() {
        return domicilio;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public void setDomicilio(String domicilio) {
        this.domicilio = domicilio;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }
}