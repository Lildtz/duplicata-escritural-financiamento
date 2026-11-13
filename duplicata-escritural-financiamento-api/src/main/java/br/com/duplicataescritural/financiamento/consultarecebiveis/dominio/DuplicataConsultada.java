package br.com.duplicataescritural.financiamento.consultarecebiveis.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record DuplicataConsultada(UUID idDuplicata, String numeroDuplicata, String cnpjSacado,
        LocalDate dataVencimento, BigDecimal valorNominal, Instant escrituradaEm) {
    public DuplicataConsultada {
        Objects.requireNonNull(idDuplicata);
        Objects.requireNonNull(dataVencimento);
        Objects.requireNonNull(valorNominal);
        Objects.requireNonNull(escrituradaEm);
        if (numeroDuplicata == null || numeroDuplicata.isBlank()
                || cnpjSacado == null || !cnpjSacado.matches("[A-Z0-9]{12}[0-9]{2}")) {
            throw new IllegalArgumentException("Identificação da Duplicata incompleta");
        }
    }
}
