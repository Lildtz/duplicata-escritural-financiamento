package br.com.duplicataescritural.signataria.escrituracao.api;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import br.com.duplicataescritural.signataria.escrituracao.aplicacao.ServicoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ExemploEscrituracao;
import br.com.duplicataescritural.signataria.seguranca.SacadorAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.TransactionTimedOutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrosEscrituracaoTests {

    private static final String DETALHE_INTERNO = "SELECT segredo FROM tabela_interna";

    @AfterEach
    void limparIdentidade() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("falhasPersistencia")
    void classificaFalhasSemExporDetalhesInternos(RuntimeException falha, int esperado) throws Exception {
        var servico = mock(ServicoEscrituracao.class);
        when(servico.consultar(any(), any())).thenThrow(falha);
        var mvc = MockMvcBuilders.standaloneSetup(new EscrituracaoController(servico))
                .setControllerAdvice(new ErrosEscrituracao())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        var principal = new SacadorAutenticado(ExemploEscrituracao.sacador().fotografia());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        var resposta = mvc.perform(get("/api/v1/escrituracoes/processamentos/" + UUID.randomUUID()))
                .andExpect(status().is(esperado)).andReturn().getResponse();

        assertThat(resposta.getContentAsString()).contains("Não foi possível confirmar a operação")
                .doesNotContain(DETALHE_INTERNO, falha.getClass().getSimpleName(), "stackTrace");
        assertThat(resposta.getContentType()).startsWith("application/problem+json");
    }

    static Stream<Arguments> falhasPersistencia() {
        return Stream.of(
                Arguments.of(new TransientDataAccessResourceException(DETALHE_INTERNO), 503),
                Arguments.of(new RecoverableDataAccessException(DETALHE_INTERNO), 503),
                Arguments.of(new TransactionTimedOutException(DETALHE_INTERNO), 503),
                Arguments.of(new TransactionSystemException(DETALHE_INTERNO, new QueryTimeoutException(DETALHE_INTERNO)), 503),
                Arguments.of(new TransactionSystemException(DETALHE_INTERNO, new SQLTransientException(DETALHE_INTERNO)), 503),
                Arguments.of(new TransactionSystemException(DETALHE_INTERNO, new SQLRecoverableException(DETALHE_INTERNO)), 503),
                Arguments.of(new DataIntegrityViolationException(DETALHE_INTERNO), 500),
                Arguments.of(new InvalidDataAccessResourceUsageException(DETALHE_INTERNO), 500),
                Arguments.of(new DataAccessResourceFailureException(DETALHE_INTERNO), 500),
                Arguments.of(new TransactionSystemException(DETALHE_INTERNO), 500),
                Arguments.of(new TransactionSystemException(DETALHE_INTERNO, new DataIntegrityViolationException(DETALHE_INTERNO)), 500),
                Arguments.of(new CannotCreateTransactionException(DETALHE_INTERNO), 500));
    }
}
