package ar.edu.utn.dds.k3003.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/**
 * Ordena el Swagger por para qué sirve cada endpoint, no por qué entidad toca.
 *
 * <p>Al presentar el TP hace falta encontrar rápido las operaciones de los flujos principales, sin
 * que queden mezcladas con las de cargar datos de prueba o limpiar la base. Los tags se declaran
 * acá para que aparezcan en este orden y no alfabético.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "DonaTrack · Donadores y Entidades",
            version = "5.0",
            description =
                "Donadores, entidades beneficiarias y sus necesidades. Los endpoints están "
                    + "agrupados por su rol en el sistema: primero los de los flujos principales, "
                    + "después los de preparación y consulta."),
    tags = {
      @Tag(
          name = "1 · Flujo principal",
          description =
              "Las operaciones que atraviesan varios módulos: registrar una necesidad (valida el "
                  + "producto contra Donaciones y consulta stock a Logística), satisfacerla "
                  + "cuando llega una entrega, registrar una queja y consultar las estadísticas "
                  + "de un donador."),
      @Tag(
          name = "2 · Precondiciones",
          description =
              "Altas, bajas y modificaciones de donadores y entidades: lo que hay que tener "
                  + "cargado antes de poder ejecutar un flujo."),
      @Tag(
          name = "3 · Consultas",
          description = "Solo lectura. Para verificar el estado de la base durante una prueba."),
      @Tag(
          name = "4 · Administración",
          description =
              "Utilidades de la base y cambios de estado o categoría de un donador hechos a mano.")
    })
public class OpenApiConfig {}
