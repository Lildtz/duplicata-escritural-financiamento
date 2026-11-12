package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ExemploEscrituracao {

    private ExemploEscrituracao() { }

    public static Endereco endereco() {
        return new Endereco("Rua de Teste", "10", "Centro", "01001-000", "3550308", "sp");
    }

    public static DadosEscrituracao dados() {
        return new DadosEscrituracao(new DadosEscrituracao.Sacado("12.ABC.345/01DE-35", "Sacado de Teste", endereco()),
                "MERCANTIL", "DUP-1", "FAT-1", 1, LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 10),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                new DadosEscrituracao.DocumentoFiscal("NFE", "123", "1"));
    }

    public static Sacador sacador() {
        return new Sacador(UUID.randomUUID(), new Cnpj("11222333000181"), "Sacador de Teste", endereco(),
                LocalDate.of(2020, 1, 1), true);
    }
}
