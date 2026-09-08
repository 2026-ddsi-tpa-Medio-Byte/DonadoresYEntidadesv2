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
      @Tag(name = "Flujo principal"),
      @Tag(name = "Administración"),
      @Tag(name = "Precondiciones"),
      @Tag(name = "Consultas")
    })
public class OpenApiConfig {}
