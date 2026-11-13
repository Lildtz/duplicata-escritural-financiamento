package br.com.duplicataescritural.financiamento.consultarecebiveis.dominio;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record SolicitacaoAutorizacao(String cnpjSacador, Instant fimVigencia, String chaveIdempotencia) {
    public SolicitacaoAutorizacao {
        cnpjSacador = cnpjSacador == null ? "" : cnpjSacador.strip().toUpperCase(Locale.ROOT);
        if (!cnpjSacador.matches("[A-Z0-9]{12}[0-9]{2}")) {
            throw new IllegalArgumentException("CNPJ completo em formato compacto obrigatório");
        }
        Objects.requireNonNull(fimVigencia, "Fim de vigência obrigatório");
        if (chaveIdempotencia == null || !chaveIdempotencia.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Chave idempotente inválida");
        }
    }
}
