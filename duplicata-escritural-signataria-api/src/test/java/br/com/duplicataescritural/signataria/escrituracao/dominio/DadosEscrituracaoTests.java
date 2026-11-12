package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DadosEscrituracaoTests {

    @Test
    void normalizaFotografiaSemArredondarValores() {
        var base = ExemploEscrituracao.dados();
        var normalizada = new DadosEscrituracao(base.sacado(), "MERCANTIL", " DUP-1 ", " FAT-1 ", 1,
                base.dataFaturamento(), base.dataVencimento(), new BigDecimal("100.0"), new BigDecimal("100"),
                new DadosEscrituracao.DocumentoFiscal("NFE", "000000123", "001"));
        assertThat(normalizada).isEqualTo(base);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100.001", "1000000000000.00"})
    void rejeitaValoresInvalidos(String valor) {
        var base = ExemploEscrituracao.dados();
        assertThatIllegalArgumentException().isThrownBy(() -> new DadosEscrituracao(base.sacado(), "MERCANTIL",
                "DUP-1", "FAT-1", 1, base.dataFaturamento(), base.dataVencimento(),
                new BigDecimal(valor), new BigDecimal(valor), base.documentoFiscal()));
    }

    @Test
    void rejeitaValorDiferenteDaFatura() {
        var base = ExemploEscrituracao.dados();
        assertThatIllegalArgumentException().isThrownBy(() -> new DadosEscrituracao(base.sacado(), "MERCANTIL",
                "DUP-1", "FAT-1", 1, base.dataFaturamento(), base.dataVencimento(),
                new BigDecimal("100.00"), new BigDecimal("99.00"), base.documentoFiscal()));
    }

    @Test
    void rejeitaVencimentoAnteriorAoFaturamento() {
        var base = ExemploEscrituracao.dados();
        assertThatIllegalArgumentException().isThrownBy(() -> new DadosEscrituracao(base.sacado(), "MERCANTIL",
                "DUP-1", "FAT-1", 1, base.dataFaturamento(), base.dataFaturamento().minusDays(1),
                base.valorFatura(), base.valorDuplicata(), base.documentoFiscal()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1})
    void rejeitaOutrasParcelas(int parcela) {
        var base = ExemploEscrituracao.dados();
        assertThatIllegalArgumentException().isThrownBy(() -> new DadosEscrituracao(base.sacado(), "MERCANTIL",
                "DUP-1", "FAT-1", parcela, base.dataFaturamento(), base.dataVencimento(),
                base.valorFatura(), base.valorDuplicata(), base.documentoFiscal()));
    }

    @Test
    void rejeitaDadosObrigatoriosAusentes() {
        var base = ExemploEscrituracao.dados();
        assertThatIllegalArgumentException().isThrownBy(() -> new DadosEscrituracao(null, "MERCANTIL", "DUP-1",
                "FAT-1", 1, base.dataFaturamento(), base.dataVencimento(), base.valorFatura(),
                base.valorDuplicata(), base.documentoFiscal()));
        assertThatIllegalArgumentException().isThrownBy(() -> new DadosEscrituracao.Sacado("11222333000181", " ", null));
    }

    @Test
    void validaPeriodoOperacionalComDataExplicita() {
        var base = ExemploEscrituracao.dados();
        var sacador = ExemploEscrituracao.sacador();
        base.validarRecebimento(sacador.inicioOperacao(), base.dataFaturamento());
        assertThatIllegalArgumentException().isThrownBy(() -> base.validarRecebimento(sacador.inicioOperacao(),
                base.dataFaturamento().minusDays(1)));
        var aindaNaoOperacional = new Sacador(sacador.id(), sacador.cnpj(), sacador.nome(), sacador.domicilio(),
                LocalDate.now().plusDays(1), true);
        assertThatIllegalArgumentException().isThrownBy(() -> base.validarRecebimento(aindaNaoOperacional.inicioOperacao(), LocalDate.now()));
    }
}
