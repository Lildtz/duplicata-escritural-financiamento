package br.com.duplicataescritural.signataria.escrituracao.aplicacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DuplicataConsultada(UUID idDuplicata, String numeroDuplicata, String cnpjSacado,
        LocalDate dataVencimento, BigDecimal valorNominal, Instant escrituradaEm) { }
