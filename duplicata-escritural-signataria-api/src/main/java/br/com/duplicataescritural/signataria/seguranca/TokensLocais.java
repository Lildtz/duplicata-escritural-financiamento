package br.com.duplicataescritural.signataria.seguranca;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record TokensLocais(List<Credencial> tokens) {

    public TokensLocais {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("Perfil local exige configuração explícita de tokens SHA-256");
        }
        tokens = List.copyOf(tokens);
        var impressoes = new HashSet<String>();
        for (var token : tokens) {
            if (!impressoes.add(token.sha256())) {
                throw new IllegalArgumentException("Impressão de token duplicada");
            }
        }
    }

    public record Credencial(String sha256, UUID sacadorId) {
        public Credencial {
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}") || sacadorId == null) {
                throw new IllegalArgumentException("Credencial local exige SHA-256 hexadecimal minúsculo e ID do Sacador");
            }
        }
    }
}
