package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.util.Objects;
import java.util.UUID;

public record ReservaExecucao(UUID idProcessamento, int tentativa) {
    public ReservaExecucao {
        Objects.requireNonNull(idProcessamento);
        if (tentativa < 1) {
            throw new IllegalArgumentException("Tentativa deve ser positiva");
        }
    }
}
