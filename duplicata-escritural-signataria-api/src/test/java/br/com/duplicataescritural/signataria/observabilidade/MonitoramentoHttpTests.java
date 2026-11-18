package br.com.duplicataescritural.signataria.observabilidade;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.aplicacao.ServicoProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ExemploEscrituracao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles({"local", "monitoramento"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class MonitoramentoHttpTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    static final String USUARIO = "operador-" + UUID.randomUUID();
    static final String SENHA = segredo();
    static final String HASH = new BCryptPasswordEncoder().encode(SENHA);
    static final String BASIC = "Basic " + Base64.getEncoder().encodeToString((USUARIO + ":" + SENHA).getBytes(StandardCharsets.UTF_8));
    private static final UUID SACADOR = UUID.randomUUID();
    private static final UUID FINANCIADOR = UUID.randomUUID();
    private static final String TOKEN_SACADOR = segredo();
    private static final String TOKEN_FINANCIADOR = segredo();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @Autowired Environment ambiente;
    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;
    @Autowired ServicoProcessamentoEscrituracao worker;

    @DynamicPropertySource
    static void credenciais(DynamicPropertyRegistry propriedades) {
        propriedades.add("signataria.monitoramento.usuario", () -> USUARIO);
        propriedades.add("signataria.monitoramento.senha-hash", () -> HASH);
        propriedades.add("signataria.autenticacao-local.tokens[0].sha256", () -> digest(TOKEN_SACADOR));
        propriedades.add("signataria.autenticacao-local.tokens[0].sacador-id", SACADOR::toString);
        propriedades.add("signataria.autenticacao-local.tokens[1].sha256", () -> digest(TOKEN_FINANCIADOR));
        propriedades.add("signataria.autenticacao-local.tokens[1].financiador-id", FINANCIADOR::toString);
    }

    @BeforeEach
    void provisionarSacadorLocal() {
        jdbc.sql("""
                INSERT INTO escrituradora.sacador (id, cnpj, nome, domicilio, inicio_operacao, habilitado)
                VALUES (:id, '11222333000181', 'Sacador local', CAST(:domicilio AS jsonb), DATE '2020-01-01', true)
                ON CONFLICT (id) DO NOTHING
                """).param("id", SACADOR).param("domicilio", json.writeValueAsString(ExemploEscrituracao.endereco())).update();
    }

    @Test
    void basicExclusivoExportaOperacaoWorkerJvmEJdbcSemSegredos(CapturedOutput logs) throws Exception {
        var recebimento = enviar("/api/v1/escrituracoes/manuais", "Bearer " + TOKEN_SACADOR, "POST",
                json.writeValueAsString(ExemploEscrituracao.dados()));
        assertThat(recebimento.statusCode()).isEqualTo(202);
        worker.processarProximo();
        var processamento = enviar(recebimento.headers().firstValue("Location").orElseThrow(), "Bearer " + TOKEN_SACADOR, "GET", null);
        assertThat(json.readTree(processamento.body()).get("status").asString()).isEqualTo("CONCLUIDO");
        var resposta = enviar("/actuator/prometheus", BASIC, "GET", null);
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type").orElseThrow()).contains("text/plain");
        assertThat(resposta.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(resposta.body()).contains("# HELP ", "# TYPE ", "jvm_memory_used_bytes", "jvm_threads_live_threads",
                "jdbc_connections_max", "hikaricp_connections", "http_server_requests_seconds_count",
                "signataria_operacao_seconds_count", "operacao=\"receber_escrituracao\"",
                "signataria_escrituracao_transicoes_total", "estado=\"CONCLUIDO\"").endsWith("\n");
        assertThat(resposta.body()).doesNotContain("11222333000181", "12ABC34501DE35", SACADOR.toString(),
                json.readTree(recebimento.body()).get("idProcessamento").asString());
        semSegredos(resposta.body() + resposta.headers().map() + logs.getAll());
        assertThat(logs.getAll()).doesNotContain("Using generated security password");
        assertThat(enviar("/actuator/prometheus", null, "GET", null).statusCode()).isEqualTo(401);
    }

    @Test
    void exportaBucketsSeletivosDaOperacaoSemAlterarTimersHttp() throws Exception {
        assertThat(enviar("/api/v1/autorizacoes-consulta/" + UUID.randomUUID(),
                "Bearer " + TOKEN_FINANCIADOR, "GET", null).statusCode()).isEqualTo(404);
        var resposta = enviar("/actuator/prometheus", BASIC, "GET", null);
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.body()).contains("# TYPE signataria_operacao_seconds histogram")
                .containsPattern("le=\"2(?:\\.0)?\"")
                .doesNotContain("http_server_requests_seconds_bucket", "http_client_requests_seconds_bucket");
        var infinito = resposta.body().lines().filter(linha -> linha.startsWith("signataria_operacao_seconds_bucket{"))
                .filter(linha -> linha.contains("operacao=\"consultar_autorizacao\"") && linha.contains("le=\"+Inf\""))
                .findFirst().orElseThrow();
        var contagem = resposta.body().lines().filter(linha -> linha.startsWith("signataria_operacao_seconds_count{"))
                .filter(linha -> linha.contains("operacao=\"consultar_autorizacao\""))
                .findFirst().orElseThrow();
        assertThat(Double.parseDouble(infinito.substring(infinito.lastIndexOf(' ') + 1)))
                .isPositive().isEqualTo(Double.parseDouble(contagem.substring(contagem.lastIndexOf(' ') + 1)));
    }

    @Test
    void rejeitaCredenciaisAusentesIncorretasEBearersDeAmbosPapeis(CapturedOutput logs) throws Exception {
        for (String credencial : List.of("", "Basic " + Base64.getEncoder().encodeToString(
                (USUARIO + ":" + segredo()).getBytes(StandardCharsets.UTF_8)),
                "Bearer " + TOKEN_SACADOR, "Bearer " + TOKEN_FINANCIADOR)) {
            var resposta = enviar("/actuator/prometheus", credencial, "GET", null);
            assertThat(resposta.statusCode()).isEqualTo(401);
            assertThat(resposta.headers().firstValue("WWW-Authenticate").orElseThrow()).startsWith("Basic");
            semSegredos(resposta.body() + resposta.headers().map());
        }
        for (String metodo : List.of("POST", "PUT", "DELETE", "HEAD")) {
            assertThat(enviar("/actuator/prometheus", BASIC, metodo, null).statusCode()).isEqualTo(403);
        }
        semSegredos(logs.getAll());
    }

    @Test
    void basicNaoSubstituiBearerNemLiberaActuatorSensivel() throws Exception {
        for (String caminho : List.of("/api/v1/escrituracoes/manuais", "/api/v1/autorizacoes-consulta",
                "/actuator/metrics", "/actuator/metrics/jvm.memory.used")) {
            String metodo = caminho.startsWith("/api/") ? "POST" : "GET";
            var resposta = enviar(caminho, BASIC, metodo, "{}");
            assertThat(resposta.statusCode()).as(caminho).isEqualTo(401);
            assertThat(resposta.headers().firstValue("WWW-Authenticate").orElseThrow()).isEqualTo("Bearer");
            semSegredos(resposta.body());
        }
        for (String caminho : List.of("env", "beans", "configprops", "heapdump", "loggers")) {
            assertThat(enviar("/actuator/" + caminho, BASIC, "GET", null).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void preservaHealthInfoSwaggerEMetricasBearer() throws Exception {
        for (String caminho : List.of("/actuator/health", "/actuator/info", "/v3/api-docs",
                "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js")) {
            assertThat(enviar(caminho, null, "GET", null).statusCode()).as(caminho).isEqualTo(200);
        }
        assertThat(enviar("/actuator/metrics", null, "GET", null).statusCode()).isEqualTo(401);
        assertThat(enviar("/actuator/metrics/jvm.memory.used", "Bearer " + TOKEN_FINANCIADOR, "GET", null).statusCode()).isEqualTo(200);
        assertThat(enviar("/api/v1/autorizacoes-consulta", "Bearer " + TOKEN_SACADOR, "POST", "{}").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> enviar(String caminho, String credencial, String metodo, String corpo) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                + ambiente.getRequiredProperty("local.server.port") + caminho))
                .timeout(Duration.ofSeconds(20));
        if (credencial != null && !credencial.isEmpty()) request.header("Authorization", credencial);
        if (caminho.equals("/actuator/prometheus")) request.header("Accept", "text/plain;version=0.0.4");
        if (corpo != null) request.header("Content-Type", "application/json");
        if (metodo.equals("POST") && caminho.equals("/api/v1/escrituracoes/manuais")) {
            request.header("Idempotency-Key", UUID.randomUUID().toString());
        }
        return HTTP.send(request.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(corpo)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void semSegredos(String conteudo) {
        assertThat(conteudo).doesNotContain(SENHA, HASH, BASIC, USUARIO, TOKEN_SACADOR, TOKEN_FINANCIADOR,
                digest(TOKEN_SACADOR), digest(TOKEN_FINANCIADOR));
    }

    private static String segredo() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (Exception falha) { throw new IllegalStateException(falha); }
    }
}
