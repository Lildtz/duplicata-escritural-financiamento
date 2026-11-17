package br.com.duplicataescritural.financiamento.observabilidade;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import br.com.duplicataescritural.financiamento.FinanciamentoApplication;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static br.com.duplicataescritural.financiamento.observabilidade.MonitoramentoHttpTests.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RotasMetricasHttpTests {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @ParameterizedTest
    @ValueSource(strings = {"base_path", "path_mapping", "base_publica", "mapping_publico",
            "porta", "endereco", "porta_endereco", "base_porta_endereco"})
    void rejeitaDesviosDeGerenciamentoAntesDeExporHttp(String caso) throws Exception {
        var propriedades = propriedades();
        int portaPrincipal;
        try (var reserva = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            portaPrincipal = reserva.getLocalPort();
        }
        propriedades.put("server.port", portaPrincipal);
        String endereco = "127.0.0.1";
        Integer portaGerenciamento = null;
        if (caso.contains("porta")) {
            try (var reserva = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
                portaGerenciamento = reserva.getLocalPort();
            }
            propriedades.put("management.server.port", portaGerenciamento);
            propriedades.put("management.server.address", endereco);
        }
        if (caso.contains("endereco")) {
            endereco = "127.0.0.2";
            propriedades.put("management.server.address", endereco);
        }
        if (caso.equals("base_path") || caso.equals("base_porta_endereco")) {
            propriedades.put("management.endpoints.web.base-path", "/manage");
        }
        if (caso.equals("path_mapping")) {
            propriedades.put("management.endpoints.web.path-mapping.prometheus", "scrape");
        }
        if (caso.equals("base_publica")) {
            propriedades.put("management.endpoints.web.base-path", "/actuator/health");
        }
        if (caso.equals("mapping_publico")) {
            propriedades.put("management.endpoints.web.path-mapping.prometheus", "health/prometheus");
        }
        assertThatThrownBy(() -> {
            try (var contexto = iniciar(propriedades)) {
                // O contexto precisa falhar antes de disponibilizar qualquer rota.
            }
        }).hasRootCauseMessage("Monitoramento exige /actuator/prometheus sem porta ou endereço próprios de gerenciamento");
        verificarPortaFechada("127.0.0.1", portaPrincipal);
        if (portaGerenciamento != null) {
            verificarPortaFechada(endereco, portaGerenciamento);
        }
    }

    private static void verificarPortaFechada(String endereco, int porta) {
        var pedido = HttpRequest.newBuilder(URI.create("http://" + endereco + ":" + porta + "/actuator/prometheus"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        assertThatThrownBy(() -> HTTP.send(pedido, HttpResponse.BodyHandlers.discarding())).isInstanceOf(IOException.class);
    }

    private ConfigurableApplicationContext iniciar(Map<String, Object> propriedades) {
        return new SpringApplicationBuilder(FinanciamentoApplication.class).profiles("local", "monitoramento")
                .initializers(ctx -> ctx.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("rotas-metricas-teste", propriedades))).run();
    }

    private Map<String, Object> propriedades() {
        var propriedades = new HashMap<String, Object>();
        propriedades.put("financiamento.monitoramento.usuario", USUARIO);
        propriedades.put("financiamento.monitoramento.senha-hash", HASH);
        propriedades.put("SIGNATARIA_TOKEN", TOKEN);
        return propriedades;
    }
}
