package br.com.duplicataescritural.financiamento.consultarecebiveis.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AutorizacaoConsulta(UUID idAutorizacao, UUID financiadorId, String cnpjSacador,
        Estado status, boolean vigente, Instant solicitadaEm, Instant inicioVigencia, Instant fimVigencia,
        Instant decididaEm, Instant revogadaEm, RevogadoPor revogadoPor) {
    public enum Estado { PENDENTE, ATIVA, RECUSADA, REVOGADA }
    public enum RevogadoPor { SACADOR, FINANCIADOR }

    public AutorizacaoConsulta {
        Objects.requireNonNull(idAutorizacao);
        Objects.requireNonNull(financiadorId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(solicitadaEm);
        Objects.requireNonNull(fimVigencia);
        if (cnpjSacador == null || !cnpjSacador.matches("[A-Z0-9]{12}[0-9]{2}")) {
            throw new IllegalArgumentException("CNPJ completo obrigatório");
        }
    }
}
