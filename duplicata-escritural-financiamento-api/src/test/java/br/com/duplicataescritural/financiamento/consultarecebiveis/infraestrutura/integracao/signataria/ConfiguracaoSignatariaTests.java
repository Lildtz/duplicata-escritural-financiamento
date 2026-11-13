package br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria;

import java.net.URI;
import java.time.Duration;

import br.com.duplicataescritural.financiamento.FinanciamentoApplication;
import br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao.ServicoConsultaRecebiveis;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.GatewayRegistradora;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ConfiguracaoSignatariaTests {
    private final String token = ContratoSignataria.token();
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracaoSignataria.class)
            .withBean(org.springframework.web.client.RestClient.Builder.class, org.springframework.web.client.RestClient::builder)
            .withBean(io.micrometer.observation.ObservationRegistry.class, io.micrometer.observation.ObservationRegistry::create)
            .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
            .withPropertyValues("financiamento.signataria.url-base=http://localhost:8081",
                    "financiamento.signataria.timeout-conexao=2s", "financiamento.signataria.timeout-leitura=5s");

    @Test
    void perfilLocalExigeCredencialSemVazarSegredoNoErroDeConfiguracao(CapturedOutput output) {
        contexto.run(ctx -> assertThat(ctx).hasFailed());
        String invalido = token + "!";
        contexto.withPropertyValues("financiamento.signataria.token=" + invalido).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("Token local do Financiador obrigatório");
            assertThat(ctx.getStartupFailure().toString()).doesNotContain(invalido);
        });
        assertThat(output.getAll()).doesNotContain(invalido);
    }

    @Test
    void vinculaConfiguracaoEInjetaPortaECasoDeUso() {
        contexto.withPropertyValues("financiamento.signataria.token=" + token).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(GatewayRegistradora.class).hasSingleBean(ServicoConsultaRecebiveis.class);
            var conexao = ctx.getBean(ConexaoSignataria.class);
            assertThat(conexao.timeoutConexao()).isEqualTo(Duration.ofSeconds(2));
            assertThat(conexao.timeoutLeitura()).isEqualTo(Duration.ofSeconds(5));
            assertThat(conexao.toString()).doesNotContain(token);
        });
    }

    @Test
    void bootstrapSemPerfilLocalContinuaSemIntegracaoOuCredenciais() {
        try (var ctx = new SpringApplicationBuilder(FinanciamentoApplication.class).web(WebApplicationType.NONE)
                .profiles("teste-sem-integracao").properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run()) {
            assertThat(ctx.getBeansOfType(GatewayRegistradora.class)).isEmpty();
            assertThat(ctx.getBeansOfType(ServicoConsultaRecebiveis.class)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://servidor-remoto:8081", "http://localhost:8081/api", "http://usuario:senha@localhost:8081",
            "http://localhost:8081?token=x", "http://localhost:8081#fragmento", "ftp://localhost", "/relativa",
            "http://localhost:0", "http://localhost:65536"})
    void rejeitaUrlInseguraOuAmbigua(String url) {
        assertThatThrownBy(() -> new ConexaoSignataria(URI.create(url), token, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"curto", "invalido\r\nHeader:valor"})
    void rejeitaTokenAusenteOuInvalido(String credencial) {
        assertThatThrownBy(() -> new ConexaoSignataria(URI.create(ContratoSignataria.BASE), credencial,
                Duration.ofSeconds(1), Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 2147483648L})
    void rejeitaTimeoutSemLimiteEfetivo(long milissegundos) {
        assertThatThrownBy(() -> new ConexaoSignataria(URI.create(ContratoSignataria.BASE), token,
                Duration.ofMillis(milissegundos), Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConexaoSignataria(URI.create(ContratoSignataria.BASE), token,
                Duration.ofSeconds(1), Duration.ofMillis(milissegundos))).isInstanceOf(IllegalArgumentException.class);
    }
}
