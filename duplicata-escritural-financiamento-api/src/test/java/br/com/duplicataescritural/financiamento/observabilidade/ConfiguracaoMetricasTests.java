package br.com.duplicataescritural.financiamento.observabilidade;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static br.com.duplicataescritural.financiamento.observabilidade.MonitoramentoHttpTests.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ConfiguracaoMetricasTests {
    @Test
    void aceitaCredenciaisExplicitasNoContextoLocal() {
        runner(configuracao()).run(contexto -> assertThat(contexto).hasNotFailed().hasSingleBean(SegurancaMetricas.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem_usuario", "sem_hash", "usuario_invalido", "hash_invalido", "hash_fraco",
            "sem_local", "bind_amplo", "porta_gerenciamento", "endereco_gerenciamento"})
    void rejeitaConfiguracaoInseguraSemRevelarCredenciais(String caso, CapturedOutput logs) {
        var propriedades = configuracao();
        switch (caso) {
            case "sem_usuario" -> propriedades.put("financiamento.monitoramento.usuario", "");
            case "sem_hash" -> propriedades.put("financiamento.monitoramento.senha-hash", "");
            case "usuario_invalido" -> propriedades.put("financiamento.monitoramento.usuario", "usuario:invalido");
            case "hash_invalido" -> propriedades.put("financiamento.monitoramento.senha-hash", SENHA);
            case "hash_fraco" -> propriedades.put("financiamento.monitoramento.senha-hash", HASH.replace("$10$", "$04$"));
            case "sem_local" -> propriedades.put("spring.profiles.active", "monitoramento");
            case "bind_amplo" -> propriedades.put("server.address", "0.0.0.0");
            case "porta_gerenciamento" -> propriedades.put("management.server.port", "0");
            case "endereco_gerenciamento" -> propriedades.put("management.server.address", "127.0.0.1");
            default -> throw new IllegalArgumentException(caso);
        }
        runner(propriedades).run(contexto -> assertThat(contexto).hasFailed()
                .getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
        assertThat(logs.getAll()).doesNotContain(SENHA, HASH, USUARIO, "Using generated security password");
    }

    private ApplicationContextRunner runner(Map<String, String> propriedades) {
        return new ApplicationContextRunner().withUserConfiguration(SegurancaMetricas.class)
                .withPropertyValues(propriedades.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new));
    }

    private Map<String, String> configuracao() {
        return new HashMap<>(Map.of("spring.profiles.active", "local,monitoramento", "server.address", "127.0.0.1",
                "financiamento.monitoramento.usuario", USUARIO, "financiamento.monitoramento.senha-hash", HASH));
    }
}
