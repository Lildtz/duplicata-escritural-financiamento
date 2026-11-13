package br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria;

import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import java.time.Clock;

import br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao.ServicoConsultaRecebiveis;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.GatewayRegistradora;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@Profile("local")
@EnableConfigurationProperties(ConexaoSignataria.class)
public class ConfiguracaoSignataria {
    @Bean
    GatewayRegistradora gatewayRegistradora(ConexaoSignataria conexao, RestClient.Builder builder) {
        var http = HttpClient.newBuilder().connectTimeout(conexao.timeoutConexao())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(conexao.timeoutLeitura());
        var client = builder.baseUrl(conexao.urlBase().toString()).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + conexao.token())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
        return new GatewayRegistradoraHttp(client);
    }

    @Bean
    ServicoConsultaRecebiveis servicoConsultaRecebiveis(GatewayRegistradora registradora, ObservationRegistry observacoes) {
        return new ServicoConsultaRecebiveis(registradora, Clock.systemUTC(), observacoes);
    }
}
