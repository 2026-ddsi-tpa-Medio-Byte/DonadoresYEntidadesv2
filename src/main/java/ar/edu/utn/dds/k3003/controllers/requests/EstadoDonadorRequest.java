package ar.edu.utn.dds.k3003.controllers.requests;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;

public record EstadoDonadorRequest(
        EstadoDonadorEnum estado
) {}