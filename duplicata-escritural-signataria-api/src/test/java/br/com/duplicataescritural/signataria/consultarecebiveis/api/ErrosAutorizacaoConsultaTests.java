package br.com.duplicataescritural.signataria.consultarecebiveis.api;

import java.sql.SQLTransientException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.TransactionSystemException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrosAutorizacaoConsultaTests {
    @ParameterizedTest
    @MethodSource("falhas")
    void classificaFalhasSemExporDiagnosticoInterno(RuntimeException falha, int status) {
        var resposta = new ErrosAutorizacaoConsulta().persistencia(falha);
        assertThat(resposta.getStatus()).isEqualTo(status);
        assertThat(resposta.getDetail()).isEqualTo("Não foi possível confirmar a operação");
        assertThat(resposta.getProperties()).containsEntry("codigo", status == 503 ? "INDISPONIBILIDADE_TEMPORARIA" : "ERRO_INTERNO");
        assertThat(resposta.toString()).doesNotContain("SELECT segredo", "tabela_interna", "stackTrace");
    }

    static Stream<Arguments> falhas() {
        String detalhe = "SELECT segredo FROM tabela_interna";
        return Stream.of(
                Arguments.of(new TransientDataAccessResourceException(detalhe), 503),
                Arguments.of(new RecoverableDataAccessException(detalhe), 503),
                Arguments.of(new TransactionSystemException(detalhe, new SQLTransientException(detalhe)), 503),
                Arguments.of(new TransactionSystemException(detalhe, new DataIntegrityViolationException(detalhe)), 500),
                Arguments.of(new DataIntegrityViolationException(detalhe), 500));
    }
}
