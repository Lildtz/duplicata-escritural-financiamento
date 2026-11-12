package br.com.duplicataescritural.signataria.seguranca;

import java.util.Objects;
import java.util.UUID;

public record FinanciadorAutenticado(UUID id) implements ParticipanteAutenticado {
    public FinanciadorAutenticado {
        Objects.requireNonNull(id);
    }
}
