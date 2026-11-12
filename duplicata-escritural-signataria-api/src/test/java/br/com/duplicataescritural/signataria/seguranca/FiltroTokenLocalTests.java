package br.com.duplicataescritural.signataria.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.Sacadores;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FiltroTokenLocalTests {

    private static final String DETALHE_INTERNO = "SELECT segredo FROM tabela_interna";

    @ParameterizedTest
    @MethodSource("falhasPersistencia")
    void classificaFalhaDoCadastroSemExporTokenOuDetalhes(DataAccessException falha, int esperado) throws Exception {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII)));
        UUID id = UUID.randomUUID();
        var configuracao = new TokensLocais(List.of(new TokensLocais.Credencial(sha256, id)));
        var sacadores = mock(Sacadores.class);
        when(sacadores.findById(id)).thenThrow(falha);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new FiltroTokenLocal(configuracao, sacadores).doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(esperado);
        assertThat(response.getContentAsString()).doesNotContain(token, sha256, DETALHE_INTERNO);
        assertThat(chain.getRequest()).isNull();
    }

    static Stream<Arguments> falhasPersistencia() {
        return Stream.of(
                Arguments.of(new TransientDataAccessResourceException(DETALHE_INTERNO), 503),
                Arguments.of(new RecoverableDataAccessException(DETALHE_INTERNO), 503),
                Arguments.of(new DataIntegrityViolationException(DETALHE_INTERNO), 500),
                Arguments.of(new InvalidDataAccessApiUsageException(DETALHE_INTERNO), 500));
    }
}
