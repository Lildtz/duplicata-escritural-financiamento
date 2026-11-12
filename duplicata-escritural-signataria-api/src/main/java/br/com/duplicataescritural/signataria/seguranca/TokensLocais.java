package br.com.duplicataescritural.signataria.seguranca;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.properties.bind.ConstructorBinding;

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

    public record Credencial(String sha256, UUID sacadorId, UUID financiadorId) {
        public Credencial(String sha256, UUID sacadorId) {
            this(sha256, sacadorId, null);
        }

        @ConstructorBinding
        public Credencial {
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}") || (sacadorId == null) == (financiadorId == null)) {
                throw new IllegalArgumentException("Credencial local exige SHA-256 e exatamente uma identidade: Sacador ou Financiador");
            }
        }
    }
}
