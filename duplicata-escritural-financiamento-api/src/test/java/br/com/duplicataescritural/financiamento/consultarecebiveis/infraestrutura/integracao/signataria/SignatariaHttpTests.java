package br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import br.com.duplicataescritural.financiamento.FinanciamentoApplication;
import br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao.ServicoConsultaRecebiveis;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.FalhaRegistradora;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;

import static br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria.ContratoSignataria.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SignatariaHttpTests {
    private HttpServer servidor;
    private final String token = token();

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.start();
    }

    @AfterEach void encerrar() { servidor.stop(0); }

    @Test
    void bootstrapLocalRealConsomeHttpComBearerEPaginacao() {
        var caminho = new AtomicReference<String>();
        var credencial = new AtomicReference<String>();
        servidor.createContext("/", exchange -> {
            caminho.set(exchange.getRequestURI().toString());
            credencial.set(exchange.getRequestHeaders().getFirst("Authorization"));
            responder(exchange, 200, PAGINA);
        });
        try (var ctx = new SpringApplicationBuilder(FinanciamentoApplication.class).web(WebApplicationType.NONE).profiles("local")
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR")
                .initializers(contexto -> contexto.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "conexao-http-de-teste", Map.of("SIGNATARIA_URL_BASE", url(), "SIGNATARIA_TOKEN", token,
                        "SIGNATARIA_TIMEOUT_CONEXAO", "2s", "SIGNATARIA_TIMEOUT_LEITURA", "2s")))).run()) {
            var resultado = ctx.getBean(ServicoConsultaRecebiveis.class).consultarDuplicatas(AUTORIZACAO, 0, 1);
            assertThat(resultado.resultado().itens().getFirst().valorNominal()).isEqualByComparingTo("999999999999.99");
            assertThat(resultado.consultadaEm()).isNotNull();
            assertThat(caminho.get()).isEqualTo(CAMINHO + "/duplicatas?pagina=0&tamanho=1");
            assertThat(credencial.get()).isEqualTo("Bearer " + token);
        }
    }

    @Test
    void timeoutDeLeituraConfiguradoEncerraEsperaSemRetry() {
        var liberar = new CountDownLatch(1);
        var chamadas = new AtomicInteger();
        servidor.createContext("/", exchange -> {
            chamadas.incrementAndGet();
            try {
                liberar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var gateway = new ConfiguracaoSignataria().gatewayRegistradora(conexao(Duration.ofMillis(200)));
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThatThrownBy(() -> gateway.consultarAutorizacao(AUTORIZACAO))
                            .isInstanceOfSatisfying(FalhaRegistradora.class,
                                    falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.TIMEOUT)));
            assertThat(chamadas.get()).isEqualTo(1);
        } finally {
            liberar.countDown();
        }
    }

    @Test
    void conexaoRecusadaProduzIndisponibilidadeExplicita() throws IOException {
        int porta;
        try (var reserva = new ServerSocket(0)) { porta = reserva.getLocalPort(); }
        var conexao = new ConexaoSignataria(URI.create("http://127.0.0.1:" + porta), token, Duration.ofSeconds(1), Duration.ofSeconds(1));
        var gateway = new ConfiguracaoSignataria().gatewayRegistradora(conexao);
        assertThatThrownBy(() -> gateway.consultarAutorizacao(AUTORIZACAO)).isInstanceOfSatisfying(FalhaRegistradora.class,
                falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.INDISPONIVEL));
    }

    @Test
    void naoSegueRedirecionamentoNemEnviaTokenAoDestino() throws IOException {
        var destino = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var chamadas = new AtomicInteger();
        destino.createContext("/", exchange -> {
            chamadas.incrementAndGet();
            responder(exchange, 200, AUTORIZACAO_ATIVA);
        });
        destino.start();
        servidor.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + destino.getAddress().getPort() + "/destino");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        try {
            var gateway = new ConfiguracaoSignataria().gatewayRegistradora(conexao(Duration.ofSeconds(2)));
            assertThatThrownBy(() -> gateway.consultarAutorizacao(AUTORIZACAO)).isInstanceOfSatisfying(FalhaRegistradora.class,
                    falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.RESPOSTA_INCOMPATIVEL));
            assertThat(chamadas.get()).isZero();
        } finally {
            destino.stop(0);
        }
    }

    private ConexaoSignataria conexao(Duration leitura) {
        return new ConexaoSignataria(URI.create(url()), token, Duration.ofSeconds(2), leitura);
    }

    private String url() { return "http://127.0.0.1:" + servidor.getAddress().getPort(); }

    private static void responder(HttpExchange exchange, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
