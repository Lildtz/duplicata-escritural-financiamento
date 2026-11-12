package br.com.duplicataescritural.signataria.escrituracao.aplicacao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("signataria.escrituracao.processamento")
public record ConfiguracaoProcessamento(@DefaultValue("false") boolean habilitado,
                                       @DefaultValue("1s") Duration polling,
                                       @DefaultValue("3") int maximoTentativas,
                                       @DefaultValue("5s") Duration esperaSegundaTentativa,
                                       @DefaultValue("30s") Duration esperaTerceiraTentativa,
                                       @DefaultValue("60s") Duration duracaoReserva,
                                       @DefaultValue("10s") Duration timeoutExecucao) {
    public ConfiguracaoProcessamento {
        if (maximoTentativas < 1 || maximoTentativas > 3) {
            throw new IllegalArgumentException("Máximo de tentativas deve estar entre 1 e 3");
        }
        for (var intervalo : new Duration[]{polling, esperaSegundaTentativa, esperaTerceiraTentativa,
                duracaoReserva, timeoutExecucao}) {
            if (intervalo == null || intervalo.isNegative() || intervalo.isZero()) {
                throw new IllegalArgumentException("Intervalos de processamento devem ser positivos");
            }
        }
        if (timeoutExecucao.getNano() != 0 || timeoutExecucao.getSeconds() > Integer.MAX_VALUE
                || duracaoReserva.compareTo(timeoutExecucao) <= 0) {
            throw new IllegalArgumentException("Timeout exige segundos inteiros e deve ser menor que a reserva");
        }
    }

    public Duration esperaApos(int tentativa) {
        return tentativa == 1 ? esperaSegundaTentativa : esperaTerceiraTentativa;
    }
}
