package ar.edu.utn.dds.k3003.catedra;

public class ClassFinder {

  public static Class findClass() {
    var classLoader = Thread.currentThread().getContextClassLoader();
    try {
      var clazz = Class.forName("ar.edu.utn.dds.k3003.Fachada", true, classLoader);
      return clazz;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("No se encontro el componente implementado");
    }
  }
}
