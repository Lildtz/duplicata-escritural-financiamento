package br.com.duplicataescritural.signataria.escrituracao.infraestrutura;

import java.time.Clock;

import br.com.duplicataescritural.signataria.escrituracao.aplicacao.ConfiguracaoProcessamento;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfiguracaoProcessamento.class)
public class ConfiguracaoEscrituracao {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock relogio() {
        return Clock.systemUTC();
    }
}
