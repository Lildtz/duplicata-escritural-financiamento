package br.com.duplicataescritural.signataria.escrituracao.aplicacao;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.ConfiguracaoEscrituracao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ConfiguracaoProcessamentoTests {
    private static final String PREFIXO = "signataria.escrituracao.processamento.";
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracaoEscrituracao.class, AgendadorEscrituracao.class)
            .withBean(ServicoProcessamentoEscrituracao.class, () -> mock(ServicoProcessamentoEscrituracao.class));

    @Test
    void padroesSaoExplicitosEAgendamentoExigeHabilitacao() {
        contexto.run(app -> {
            assertThat(app).hasNotFailed().doesNotHaveBean(AgendadorEscrituracao.class);
            var configuracao = app.getBean(ConfiguracaoProcessamento.class);
            assertThat(configuracao.habilitado()).isFalse();
            assertThat(configuracao.maximoTentativas()).isEqualTo(3);
            assertThat(configuracao.polling()).isEqualTo(Duration.ofSeconds(1));
            assertThat(configuracao.esperaApos(1)).isEqualTo(Duration.ofSeconds(5));
            assertThat(configuracao.esperaApos(2)).isEqualTo(Duration.ofSeconds(30));
            assertThat(configuracao.duracaoReserva()).isEqualTo(Duration.ofSeconds(60));
            assertThat(configuracao.timeoutExecucao()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"maximo-tentativas=0", "maximo-tentativas=4", "polling=0s", "duracao-reserva=10s",
            "timeout-execucao=1ms", "espera-segunda-tentativa=-1s", "espera-terceira-tentativa=0s"})
    void rejeitaPoliticaInvalidaNaInicializacao(String propriedade) {
        contexto.withPropertyValues(PREFIXO + propriedade).run(app -> assertThat(app).hasFailed());
    }

    @Test
    void agendamentoHabilitadoChamaCoordenadorSemEsperaArbitraria() {
        var chamado = new CountDownLatch(1);
        var servico = mock(ServicoProcessamentoEscrituracao.class);
        doAnswer(invocacao -> { chamado.countDown(); return null; }).when(servico).processarProximo();
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracaoEscrituracao.class, AgendadorEscrituracao.class)
                .withBean(ServicoProcessamentoEscrituracao.class, () -> servico)
                .withPropertyValues(PREFIXO + "habilitado=true", PREFIXO + "polling=1h")
                .run(app -> {
                    assertThat(app).hasNotFailed().hasSingleBean(AgendadorEscrituracao.class);
                    assertThat(chamado.await(5, TimeUnit.SECONDS)).isTrue();
                });
    }
}
