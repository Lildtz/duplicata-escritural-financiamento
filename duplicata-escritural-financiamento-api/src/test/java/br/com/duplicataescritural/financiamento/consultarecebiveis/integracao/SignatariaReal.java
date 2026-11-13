package br.com.duplicataescritural.financiamento.consultarecebiveis.integracao;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Fixture externa: conhece contratos HTTP/configuração e provisiona somente o cadastro local.
final class SignatariaReal implements AutoCloseable {
    static final UUID SACADOR = UUID.randomUUID();
    static final UUID OUTRO_SACADOR = UUID.randomUUID();
    static final UUID SACADOR_SEM_TITULOS = UUID.randomUUID();
    static final UUID FINANCIADOR = SACADOR; // Igualdade de UUID não deve confundir os papéis.
    static final UUID OUTRO_FINANCIADOR = UUID.randomUUID();
    static final String CNPJ = "12ABC34501DE35";
    static final String OUTRO_CNPJ = "11222333000181";
    static final String CNPJ_SEM_TITULOS = "11444777000161";
    static final String TOKEN_SACADOR = token();
    static final String TOKEN_OUTRO_SACADOR = token();
    static final String TOKEN_SACADOR_SEM_TITULOS = token();
    static final String TOKEN_FINANCIADOR = token();
    static final String TOKEN_OUTRO_FINANCIADOR = token();
    static final String TOKEN_INVALIDO = token();
    private static final List<String> TOKENS = List.of(TOKEN_SACADOR, TOKEN_OUTRO_SACADOR,
            TOKEN_SACADOR_SEM_TITULOS, TOKEN_FINANCIADOR, TOKEN_OUTRO_FINANCIADOR, TOKEN_INVALIDO);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final Pattern PORTA = Pattern.compile("Tomcat started on port (\\d+)");
    final JsonMapper json = JsonMapper.builder().build();
    private final Path log;
    private Process processo;
    String url;

    SignatariaReal(Path log) {
        this.log = log;
    }

    void iniciar(PostgreSQLContainer postgres, String otlp) throws Exception {
        var configuracao = new java.util.HashMap<String, Object>(Map.of(
                "spring.profiles.active", "local",
                "spring.datasource.url", postgres.getJdbcUrl(),
                "spring.datasource.username", postgres.getUsername(),
                "spring.datasource.password", postgres.getPassword(),
                "server.port", "0",
                "server.address", "127.0.0.1",
                "signataria.escrituracao.processamento.habilitado", "true",
                "signataria.escrituracao.processamento.polling", "100ms",
                "signataria.autenticacao-local.tokens", List.of(
                        Map.of("sha256", digest(TOKEN_SACADOR), "sacador-id", SACADOR),
                        Map.of("sha256", digest(TOKEN_OUTRO_SACADOR), "sacador-id", OUTRO_SACADOR),
                        Map.of("sha256", digest(TOKEN_SACADOR_SEM_TITULOS), "sacador-id", SACADOR_SEM_TITULOS),
                        Map.of("sha256", digest(TOKEN_FINANCIADOR), "financiador-id", FINANCIADOR),
                        Map.of("sha256", digest(TOKEN_OUTRO_FINANCIADOR), "financiador-id", OUTRO_FINANCIADOR))));
        configuracao.put("management.opentelemetry.tracing.export.otlp.endpoint", otlp);
        configuracao.put("management.opentelemetry.tracing.export.schedule-delay", "100ms");
        configuracao.put("management.tracing.sampling.probability", "1.0");
        configuracao.put("management.otlp.metrics.export.enabled", "false");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", System.getProperty("signataria.jar"), "--spring.main.banner-mode=off")
                .redirectErrorStream(true).redirectOutput(log.toFile());
        // Credenciais efêmeras não aparecem em argumentos, arquivos de configuração ou relatórios.
        builder.environment().put("SPRING_APPLICATION_JSON", json.writeValueAsString(configuracao));
        processo = builder.start();
        await().alias("Signatária real pronta por HTTP").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(200)).until(() -> {
                    if (!processo.isAlive()) {
                        throw new IllegalStateException("Signatária encerrou durante o bootstrap; consulte o log temporário");
                    }
                    var porta = PORTA.matcher(Files.readString(log));
                    if (!porta.find()) return false;
                    url = "http://127.0.0.1:" + porta.group(1);
                    return enviar(null, "GET", "/actuator/health", null, null).statusCode() == 200;
                });
        sql(postgres, "SELECT count(*) FROM escrituradora.flyway_schema_history WHERE success AND version IN ('1','2','3')", "3");
        cadastrar(postgres, SACADOR, CNPJ);
        cadastrar(postgres, OUTRO_SACADOR, OUTRO_CNPJ);
        cadastrar(postgres, SACADOR_SEM_TITULOS, CNPJ_SEM_TITULOS);
    }

    HttpResponse<String> enviar(String token, String metodo, String caminho, Object corpo, String chave) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url + caminho)).timeout(Duration.ofSeconds(5));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (chave != null) request.header("Idempotency-Key", chave);
        if (corpo != null) request.header("Content-Type", "application/json");
        return HTTP.send(request.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String logs() throws Exception {
        return Files.readString(log);
    }

    private void cadastrar(PostgreSQLContainer postgres, UUID id, String cnpj) throws Exception {
        // Não há endpoint de cadastro. A fixture não insere autorizações, processamentos ou duplicatas.
        sql(postgres, "INSERT INTO escrituradora.sacador VALUES ('" + id + "','" + cnpj
                + "','Sacador fictício','{\"logradouro\":\"Rua Teste\",\"numero\":\"1\",\"bairro\":\"Centro\","
                + "\"cep\":\"01001000\",\"municipioIbge\":\"3550308\",\"uf\":\"SP\"}', '2020-01-01',true)", "INSERT 0 1");
    }

    private void sql(PostgreSQLContainer postgres, String comando, String esperado) throws Exception {
        var resultado = postgres.execInContainer("psql", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-tAc", comando);
        assertThat(resultado.getExitCode()).as("Provisionamento técnico/Flyway").isZero();
        assertThat(resultado.getStdout().strip()).isEqualTo(esperado);
    }

    @Override
    public void close() throws Exception {
        if (processo != null) {
            processo.destroy();
            if (!processo.waitFor(10, TimeUnit.SECONDS)) {
                processo.destroyForcibly();
                assertThat(processo.waitFor(10, TimeUnit.SECONDS)).as("Encerramento da Signatária").isTrue();
            }
        }
        if (Files.exists(log)) {
            String conteudo = Files.readString(log);
            verificarAusenciaDeCredenciais(conteudo);
        }
    }

    static void verificarAusenciaDeCredenciais(String conteudo) {
        assertThat(TOKENS.stream().noneMatch(t -> conteudo.contains(t) || conteudo.contains(digestSemErro(t))))
                .as("Sinais sem Bearers ou hashes de teste").isTrue();
    }

    private static String digestSemErro(String token) {
        try { return digest(token); } catch (Exception falha) { throw new IllegalStateException(falha); }
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
