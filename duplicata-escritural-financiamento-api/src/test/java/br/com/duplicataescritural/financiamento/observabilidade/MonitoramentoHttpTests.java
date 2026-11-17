package br.com.duplicataescritural.financiamento.observabilidade;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.duplicataescritural.financiamento.FinanciamentoApplication;
import br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao.ServicoConsultaRecebiveis;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MonitoramentoHttpTests {
    static final String USUARIO = "operador-" + UUID.randomUUID();
    static final String SENHA = segredo();
    static final String HASH = new BCryptPasswordEncoder().encode(SENHA);
    static final String TOKEN = segredo();
    static final String BASIC = "Basic " + Base64.getEncoder().encodeToString((USUARIO + ":" + SENHA).getBytes(StandardCharsets.UTF_8));
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static ConfigurableApplicationContext banco;
    private static HttpServer signataria;

    @BeforeAll
    static void iniciar() throws Exception {
        signataria = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        signataria.createContext("/", exchange -> {
            byte[] resposta = """
                    {"cnpjSacador":"11222333000181","escopo":"LOCAL_SIMULADO",
                     "itens":[],"pagina":0,"tamanho":1,"temProximaPagina":false}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resposta.length);
            try (var corpo = exchange.getResponseBody()) { corpo.write(resposta); }
        });
        signataria.start();
        banco = iniciar(Map.of("financiamento.monitoramento.usuario", USUARIO,
                "financiamento.monitoramento.senha-hash", HASH), "local", "monitoramento");
    }

    @AfterAll
    static void encerrar() {
        try { if (banco != null) banco.close(); }
        finally { if (signataria != null) signataria.stop(0); }
    }

    @Test
    void basicExclusivoExportaMetricasReaisSemSessaoOuSegredos(CapturedOutput logs) throws Exception {
        banco.getBean(ServicoConsultaRecebiveis.class).consultarDuplicatas(UUID.randomUUID(), 0, 1);
        assertThat(enviar(banco, "/actuator/health", null, "GET").statusCode()).isEqualTo(200);
        var resposta = enviar(banco, "/actuator/prometheus", BASIC, "GET");
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type").orElseThrow()).contains("text/plain");
        assertThat(resposta.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(resposta.body()).contains("# HELP ", "# TYPE ", "jvm_memory_used_bytes",
                "jvm_threads_live_threads", "http_server_requests_seconds_count", "http_client_requests_seconds_count",
                "financiamento_registradora_operacao_seconds_count", "operacao=\"consultar_duplicatas\"",
                "resultado=\"sucesso\"").endsWith("\n");
        assertThat(resposta.body()).doesNotContain("11222333000181", "pagina=0", "tamanho=1");
        semSegredos(resposta.body() + resposta.headers().map() + logs.getAll());
        assertThat(logs.getAll()).doesNotContain("Using generated security password");
        assertThat(enviar(banco, "/actuator/prometheus", null, "GET").statusCode()).isEqualTo(401);
    }

    @Test
    void rejeitaAusenciaErroBearerEMetodosDiferentesDeGet(CapturedOutput logs) throws Exception {
        for (String credencial : List.of("", "Basic " + Base64.getEncoder().encodeToString(
                (USUARIO + ":" + segredo()).getBytes(StandardCharsets.UTF_8)), "Bearer " + TOKEN)) {
            var resposta = enviar(banco, "/actuator/prometheus", credencial, "GET");
            assertThat(resposta.statusCode()).isEqualTo(401);
            assertThat(resposta.headers().firstValue("WWW-Authenticate").orElseThrow()).startsWith("Basic");
            semSegredos(resposta.body() + resposta.headers().map());
        }
        for (String metodo : List.of("POST", "PUT", "DELETE", "HEAD")) {
            assertThat(enviar(banco, "/actuator/prometheus", BASIC, metodo).statusCode()).isEqualTo(403);
        }
        semSegredos(logs.getAll());
    }

    @Test
    void basicNaoAbreRotasDeNegocioLoginOuActuatorSensivel() throws Exception {
        for (String caminho : List.of("/api/v1/autorizacoes-consulta", "/api/v1/escrituracoes/manuais",
                "/login", "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/heapdump", "/actuator/loggers", "/actuator/metrics")) {
            var resposta = enviar(banco, caminho, BASIC, "GET");
            assertThat(resposta.statusCode()).as(caminho).isEqualTo(404);
            semSegredos(resposta.body());
        }
        assertThat(enviar(banco, "/actuator/info", null, "GET").statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "local"})
    void semMonitoramentoMantemHttpSemCredenciaisOperacionais(String perfil, CapturedOutput logs) throws Exception {
        try (var contexto = iniciar(Map.of(), perfil)) {
            assertThat(contexto.getBean(WebEndpointsSupplier.class).getEndpoints())
                    .extracting(endpoint -> endpoint.getEndpointId().toString()).doesNotContain("prometheus");
            for (String caminho : List.of("/actuator", "/actuator/health", "/actuator/info")) {
                assertThat(enviar(contexto, caminho, null, "GET").statusCode()).isEqualTo(200);
            }
            for (String caminho : List.of("/actuator/prometheus", "/actuator/metrics", "/actuator/env",
                    "/actuator/beans", "/actuator/configprops", "/actuator/heapdump", "/actuator/loggers",
                    "/api/v1/autorizacoes-consulta", "/login")) {
                for (String credencial : List.of("", BASIC)) {
                    var resposta = enviar(contexto, caminho, credencial, "GET");
                    assertThat(resposta.statusCode()).as(caminho).isEqualTo(404);
                    assertThat(resposta.headers().allValues("WWW-Authenticate")).isEmpty();
                    semSegredos(resposta.body());
                }
            }
            assertThat(enviar(contexto, "/api/v1/autorizacoes-consulta", BASIC, "POST").statusCode()).isEqualTo(404);
        }
        assertThat(logs.getAll()).doesNotContain("Using generated security password");
    }

    private static ConfigurableApplicationContext iniciar(Map<String, Object> extras, String... perfis) {
        var propriedades = new java.util.HashMap<String, Object>(extras);
        propriedades.put("server.port", "0");
        propriedades.put("SIGNATARIA_TOKEN", TOKEN);
        propriedades.put("SIGNATARIA_URL_BASE", "http://127.0.0.1:" + signataria.getAddress().getPort());
        return new SpringApplicationBuilder(FinanciamentoApplication.class).profiles(perfis)
                .initializers(ctx -> ctx.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("http-metricas-teste", propriedades))).run();
    }

    private static HttpResponse<String> enviar(ConfigurableApplicationContext contexto, String caminho,
            String credencial, String metodo) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                + contexto.getEnvironment().getRequiredProperty("local.server.port") + caminho))
                .timeout(Duration.ofSeconds(10));
        if (caminho.equals("/actuator/prometheus")) request.header("Accept", "text/plain;version=0.0.4");
        if (credencial != null && !credencial.isEmpty()) request.header("Authorization", credencial);
        return HTTP.send(request.method(metodo, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void semSegredos(String conteudo) {
        assertThat(conteudo).doesNotContain(SENHA, HASH, TOKEN, BASIC, USUARIO);
    }

    static String segredo() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
