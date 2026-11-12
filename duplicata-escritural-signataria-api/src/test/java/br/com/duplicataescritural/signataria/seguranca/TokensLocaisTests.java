package br.com.duplicataescritural.signataria.seguranca;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class TokensLocaisTests {

    @Test
    void exigeUmUnicoPapelPorCredencial() {
        UUID id = UUID.randomUUID();
        new TokensLocais(List.of(new TokensLocais.Credencial("a".repeat(64), null, id)));
        assertThatIllegalArgumentException().isThrownBy(() -> new TokensLocais.Credencial("a".repeat(64), id, id));
        assertThatIllegalArgumentException().isThrownBy(() -> new TokensLocais.Credencial("a".repeat(64), null, null));
    }

    @Test
    void naoAceitaConfiguracaoAusenteOuVazia() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TokensLocais(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TokensLocais(List.of()));
    }

    @Test
    void naoAceitaTokenEmClaroNemDigestInvalido() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TokensLocais.Credencial("nao-e-sha256", UUID.randomUUID()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TokensLocais.Credencial("a".repeat(64), null));
    }

    @Test
    void naoPermiteAssociacaoAmbigua() {
        var primeira = new TokensLocais.Credencial("a".repeat(64), UUID.randomUUID());
        var segunda = new TokensLocais.Credencial("a".repeat(64), UUID.randomUUID());
        assertThatIllegalArgumentException().isThrownBy(() -> new TokensLocais(List.of(primeira, segunda)));
    }

    @Test
    void perfilLocalSemCredenciaisImpedeInicializacao() {
        var ambiente = new StandardEnvironment();
        ambiente.setActiveProfiles("local");
        assertThatIllegalStateException().isThrownBy(() -> new SegurancaEscrituracao().seguranca(null, ambiente, null));
    }
}
