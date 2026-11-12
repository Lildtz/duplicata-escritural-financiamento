package br.com.duplicataescritural.signataria.escrituracao.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CnpjTests {

    @ParameterizedTest
    @ValueSource(strings = {"11222333000181", "11.222.333/0001-81", " 11222333000181 "})
    void normalizaCnpjNumerico(String entrada) {
        assertThat(new Cnpj(entrada).valor()).isEqualTo("11222333000181");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12ABC34501DE35", "12.ABC.345/01DE-35", " 12.abc.345/01de-35 "})
    void validaExemploOficialDoManualDaReceita(String entrada) {
        assertThat(new Cnpj(entrada)).isEqualTo(new Cnpj("12ABC34501DE35"));
    }

    @Test
    void validaPrimeiroCnpjAlfanumericoDivulgadoPelaReceita() {
        assertThat(new Cnpj("00.000.000/E08G-12").valor()).isEqualTo("00000000E08G12");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00000000000000", "11111111111111", "11222333000180", "12ABC34501DE34",
            "12ABC34501DE45", "1122233300018", "112223330001811", "12ABC34501DEAA", "12ABC!34501DE35",
            "12ＡBC34501DE35", "12.ABC345/01DE-35", "12ABC345 01DE35", "12ABC34501DE35\nX"})
    void rejeitaFormatoOuDigitosInvalidos(String entrada) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Cnpj(entrada));
    }
}
