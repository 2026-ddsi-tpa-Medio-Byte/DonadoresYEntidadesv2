package ar.edu.utn.dds.k3003;

  
import java.util.List;


import static org.mockito.Mockito.*;
import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.ClassFinder;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.InsigniaDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.TipoMisionEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@EnabledIf("ar.edu.utn.dds.k3003.catedra.donadoresyentidades.DonadoresTest#condicion")
public class IncentivosTest{

  FachadaIncentivos instancia;
  @Mock FachadaDonaciones fachadaDonaciones;
  @Mock FachadaDonadoresYEntidades fachadaDonadores;

  MisionDTO misionEjemplo;
  InsigniaDTO insigniaEjemplo;
  
  PaqueteDTO paqueteEjemplo;
  AsignacionDTO asignacionEjemplo;
  
  @SneakyThrows
  @BeforeEach
  void setUp() {

    var clazz = ClassFinder.findClass();
    instancia = (FachadaIncentivos) clazz.getDeclaredConstructor().newInstance();

    instancia.setFachadaDonaciones(fachadaDonaciones);
    instancia.setFachadaDonadores(fachadaDonadores);
    
  }
