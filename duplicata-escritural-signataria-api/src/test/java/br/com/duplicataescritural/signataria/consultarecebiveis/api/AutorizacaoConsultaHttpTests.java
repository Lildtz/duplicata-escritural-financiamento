package br.com.duplicataescritural.signataria.consultarecebiveis.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ExemploEscrituracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "signataria.escrituracao.processamento.habilitado=false")
class AutorizacaoConsultaHttpTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18-alpine");
    private static final String BASE = "/api/v1/autorizacoes-consulta";
    private static final Instant AGORA = Instant.parse("2030-01-01T00:00:00Z");
    private static final UUID SACADOR_A = UUID.randomUUID();
    private static final UUID SACADOR_B = UUID.randomUUID();
    private static final UUID FINANCIADOR_A = SACADOR_A; // Mesmo UUID não concede o outro papel.
    private static final UUID FINANCIADOR_B = UUID.randomUUID();
    private static final List<String> TOKENS = java.util.stream.IntStream.range(0, 7).mapToObj(i -> token()).toList();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;
    @Autowired Environment ambiente;
    @Autowired RelogioControlavel relogio;

    @DynamicPropertySource
    static void credenciais(DynamicPropertyRegistry propriedades) {
        for (int i = 0; i < TOKENS.size(); i++) {
            int indice = i;
            propriedades.add("signataria.autenticacao-local.tokens[" + i + "].sha256", () -> digest(TOKENS.get(indice)));
            if (i == 2 || i == 3 || i == 5) {
                propriedades.add("signataria.autenticacao-local.tokens[" + i + "].financiador-id",
                        () -> (indice == 3 ? FINANCIADOR_B : FINANCIADOR_A).toString());
            } else {
                UUID sacador = i == 0 ? SACADOR_A : i == 1 ? SACADOR_B : UUID.randomUUID();
                propriedades.add("signataria.autenticacao-local.tokens[" + i + "].sacador-id", sacador::toString);
            }
        }
    }

    @BeforeEach
    void preparar() {
        relogio.instante.set(AGORA);
        jdbc.sql("DELETE FROM escrituradora.autorizacao_consulta").update();
        jdbc.sql("DELETE FROM escrituradora.processamento_escrituracao").update();
        jdbc.sql("DELETE FROM escrituradora.duplicata").update();
        jdbc.sql("DELETE FROM escrituradora.sacador").update();
        cadastrar(SACADOR_A, "11222333000181");
        cadastrar(SACADOR_B, "12ABC34501DE35");
    }

    @Test
    void autenticaPorPapelEIsolaParticipantes() throws Exception {
        assertThat(enviar(-1, "POST", BASE, entrada(), "chave").statusCode()).isEqualTo(401);
        assertThat(enviar(6, "POST", BASE, entrada(), "chave").statusCode()).isEqualTo(401);
        assertThat(enviar(0, "POST", BASE, entrada(), "chave").statusCode()).isEqualTo(403);
        assertThat(enviar(2, "POST", "/api/v1/escrituracoes/manuais", json.createObjectNode(), "chave").statusCode()).isEqualTo(403);
        assertThat(enviar(2, "GET", "/api/v1/escrituracoes/processamentos/" + UUID.randomUUID(), null, null).statusCode()).isEqualTo(403);
        String caminho = solicitar("papel");
        assertThat(enviar(0, "GET", caminho, null, null).statusCode()).isEqualTo(200);
        assertThat(enviar(2, "GET", caminho, null, null).statusCode()).isEqualTo(200);
        assertThat(enviar(5, "GET", caminho, null, null).statusCode()).isEqualTo(200);
        for (int outro : List.of(1, 3)) {
            assertThat(enviar(outro, "GET", caminho, null, null).statusCode()).isEqualTo(404);
            assertThat(enviar(outro, "DELETE", caminho, null, null).statusCode()).isEqualTo(404);
        }
        assertThat(enviar(1, "PUT", caminho + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(404);
        assertThat(enviar(2, "PUT", caminho + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(403);
        assertThat(enviar(2, "GET", BASE + "/" + UUID.randomUUID(), null, null).statusCode()).isEqualTo(404);
        assertThat(enviar(-1, "GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
        assertThat(enviar(2, "GET", caminho + "/duplicatas", null, null).statusCode()).isEqualTo(403);
    }

    @Test
    void aprovaRevogaEPreservaRecebimentoIdempotente() throws Exception {
        var original = enviar(2, "POST", BASE, entrada(), "ciclo");
        assertThat(original.statusCode()).isEqualTo(201);
        String caminho = original.headers().firstValue("Location").orElseThrow();
        var recebimento = json.readTree(original.body());
        assertThat(recebimento.get("financiadorId").asString()).isEqualTo(FINANCIADOR_A.toString());
        assertThat(recebimento.get("status").asString()).isEqualTo("PENDENTE");
        assertThat(recebimento.get("vigente").asBoolean()).isFalse();
        relogio.instante.set(AGORA.plusSeconds(1));
        var aprovada = enviar(0, "PUT", caminho + "/decisao", decisao("APROVAR"), null);
        assertThat(aprovada.statusCode()).isEqualTo(200);
        assertThat(json.readTree(aprovada.body()).get("vigente").asBoolean()).isTrue();
        relogio.instante.set(AGORA.plusSeconds(2));
        assertThat(enviar(0, "PUT", caminho + "/decisao", decisao("APROVAR"), null).body()).isEqualTo(aprovada.body());
        assertThat(enviar(0, "PUT", caminho + "/decisao", decisao("RECUSAR"), null).statusCode()).isEqualTo(409);
        assertThat(enviar(5, "POST", BASE, entrada().put("cnpjSacador", " 11.222.333/0001-81 "), "ciclo").body()).isEqualTo(original.body());
        assertThat(enviar(0, "DELETE", caminho, null, null).statusCode()).isEqualTo(204);
        var revogada = enviar(2, "GET", caminho, null, null);
        assertThat(json.readTree(revogada.body()).get("status").asString()).isEqualTo("REVOGADA");
        assertThat(json.readTree(revogada.body()).get("revogadoPor").asString()).isEqualTo("SACADOR");
        relogio.instante.set(AGORA.plusSeconds(3));
        assertThat(enviar(2, "DELETE", caminho, null, null).statusCode()).isEqualTo(204);
        assertThat(enviar(2, "GET", caminho, null, null).body()).isEqualTo(revogada.body());
        assertThat(enviar(0, "PUT", caminho + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(409);
        assertThat(enviar(2, "POST", BASE, entrada(), "ciclo").body()).isEqualTo(original.body());
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void recusaECancelaPendenteSemReabrir() throws Exception {
        String recusada = solicitar("recusa");
        var resposta = enviar(0, "PUT", recusada + "/decisao", decisao("RECUSAR"), null);
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(resposta.body()).get("status").asString()).isEqualTo("RECUSADA");
        assertThat(enviar(0, "PUT", recusada + "/decisao", decisao("RECUSAR"), null).body()).isEqualTo(resposta.body());
        assertThat(enviar(0, "PUT", recusada + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(409);
        assertThat(enviar(2, "DELETE", recusada, null, null).statusCode()).isEqualTo(409);
        String cancelada = solicitar("cancelada");
        assertThat(enviar(2, "DELETE", cancelada, null, null).statusCode()).isEqualTo(204);
        assertThat(json.readTree(enviar(2, "GET", cancelada, null, null).body()).get("revogadoPor").asString()).isEqualTo("FINANCIADOR");
        assertThat(enviar(0, "PUT", cancelada + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(409);
    }

    @Test
    void expiracaoNaoMudaEstadoPersistidoERepeticaoNaoRenovaVigencia() throws Exception {
        String pendente = solicitar("expira-pendente");
        String ativa = solicitar("expira-ativa");
        assertThat(enviar(0, "PUT", ativa + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(200);
        relogio.instante.set(AGORA.plusSeconds(60));
        assertThat(enviar(0, "PUT", pendente + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(409);
        var expirou = json.readTree(enviar(2, "GET", ativa, null, null).body());
        assertThat(expirou.get("status").asString()).isEqualTo("ATIVA");
        assertThat(expirou.get("vigente").asBoolean()).isFalse();
        assertThat(json.readTree(enviar(0, "PUT", ativa + "/decisao", decisao("APROVAR"), null).body())).isEqualTo(expirou);
        assertThat(enviar(2, "POST", BASE, entrada(), "expira-ativa").statusCode()).isEqualTo(201);
        assertThat(enviar(2, "POST", BASE, entrada(), "nova-expirada").statusCode()).isEqualTo(422);
        assertThat(enviar(2, "DELETE", pendente, null, null).statusCode()).isEqualTo(204);
    }

    @Test
    void validaContratoIdentidadesEVigenciaSemPersistirDadosInvalidos() throws Exception {
        assertThat(enviar(2, "POST", BASE, entrada(), null).statusCode()).isEqualTo(400);
        assertThat(enviar(2, "POST", BASE, entrada(), "invalida!").statusCode()).isEqualTo(400);
        assertThat(enviar(2, "POST", BASE, entrada().put("financiadorId", FINANCIADOR_B.toString()), "forjada").statusCode()).isEqualTo(400);
        assertThat(enviar(2, "POST", BASE, entrada().put("sacadorId", SACADOR_B.toString()), "forjada-sacador").statusCode()).isEqualTo(400);
        assertThat(enviar(2, "POST", BASE, entrada().put("cnpjSacador", "inválido"), "cnpj").statusCode()).isEqualTo(422);
        assertThat(enviar(2, "POST", BASE, entrada().put("fimVigencia", AGORA.toString()), "data").statusCode()).isEqualTo(422);
        assertThat(enviar(2, "POST", BASE, json.createObjectNode(), "vazia").statusCode()).isEqualTo(422);
        assertThat(enviar(2, "GET", BASE + "/uuid-invalido", null, null).statusCode()).isEqualTo(400);
        assertThat(contar()).isZero();
        String caminho = solicitar("valida");
        assertThat(enviar(0, "PUT", caminho + "/decisao", json.createObjectNode(), null).statusCode()).isEqualTo(422);
        assertThat(enviar(0, "PUT", caminho + "/decisao", decisao("OUTRA"), null).statusCode()).isEqualTo(400);
        jdbc.sql("UPDATE escrituradora.sacador SET habilitado = false WHERE id = :id").param("id", SACADOR_A).update();
        assertThat(enviar(0, "PUT", caminho + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(403);
        assertThat(enviar(2, "POST", BASE, entrada(), "desabilitado").statusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"financiadorId", "sacadorId", "estado", "campoDesconhecido"})
    void rejeitaPropriedadeDesconhecidaPorHttpSemPersistirOuConsumirChave(String campo) throws Exception {
        var corpo = entrada().put(campo, campo.equals("estado") ? "ATIVA" : UUID.randomUUID().toString());
        var rejeitada = enviar(2, "POST", BASE, corpo, "json-desconhecido");

        assertThat(rejeitada.statusCode()).isEqualTo(400);
        assertThat(rejeitada.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(json.readTree(rejeitada.body()).get("codigo").asString()).isEqualTo("REQUISICAO_INVALIDA");
        assertThat(contar()).isZero();

        var aceita = enviar(2, "POST", BASE, entrada(), "json-desconhecido");
        assertThat(aceita.statusCode()).isEqualTo(201);
        var autorizacao = json.readTree(aceita.body());
        assertThat(autorizacao.get("financiadorId").asString()).isEqualTo(FINANCIADOR_A.toString());
        assertThat(autorizacao.get("cnpjSacador").asString()).isEqualTo("11222333000181");
        assertThat(autorizacao.get("status").asString()).isEqualTo("PENDENTE");
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void chaveNormalizadaTemEscopoPorFinanciadorEConteudoImutavel() throws Exception {
        var original = enviar(2, "POST", BASE, entrada().put("fimVigencia", "2030-01-01T01:01:00+01:00"), "chave");
        assertThat(original.statusCode()).isEqualTo(201);
        assertThat(enviar(5, "POST", BASE, entrada().put("fimVigencia", "2030-01-01T00:01:00.000000123Z"), "chave").body()).isEqualTo(original.body());
        assertThat(enviar(2, "POST", BASE, entrada().put("cnpjSacador", "12abc34501de35"), "chave").statusCode()).isEqualTo(409);
        assertThat(enviar(2, "POST", BASE, entrada().put("fimVigencia", AGORA.plusSeconds(61).toString()), "chave").statusCode()).isEqualTo(409);
        assertThat(enviar(3, "POST", BASE, entrada(), "chave").statusCode()).isEqualTo(201);
        assertThat(enviar(2, "POST", BASE, entrada(), "Chave").statusCode()).isEqualTo(201);
        assertThat(contar()).isEqualTo(3);
    }

    @Test
    void postConcorrenteTemUmVencedorEConflitosNaoAbortamOutrasRequisicoes() throws Exception {
        var iguais = concorrer(java.util.stream.IntStream.range(0, 6)
                .<Callable<HttpResponse<String>>>mapToObj(i -> () -> enviar(2, "POST", BASE, entrada(), "disputa")).toList());
        assertThat(iguais).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(201));
        assertThat(iguais.stream().map(HttpResponse::body).distinct().count()).isEqualTo(1);
        var diferentes = concorrer(java.util.stream.IntStream.range(0, 6)
                .<Callable<HttpResponse<String>>>mapToObj(i -> () -> enviar(2, "POST", BASE,
                        entrada().put("fimVigencia", AGORA.plusSeconds(120 + i).toString()), "conflito")).toList());
        assertThat(diferentes.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(1);
        assertThat(diferentes.stream().filter(r -> r.statusCode() == 409).count()).isEqualTo(5);
        assertThat(contar()).isEqualTo(2);
    }

    @Test
    void aprovacaoERevogacaoConcorrentesSempreTerminamRevogadas() throws Exception {
        String caminho = solicitar("disputa-estado");
        var respostas = concorrer(List.of(
                () -> enviar(0, "PUT", caminho + "/decisao", decisao("APROVAR"), null),
                () -> enviar(2, "DELETE", caminho, null, null)));
        assertThat(respostas.get(0).statusCode()).isIn(200, 409);
        assertThat(respostas.get(1).statusCode()).isEqualTo(204);
        assertThat(json.readTree(enviar(2, "GET", caminho, null, null).body()).get("status").asString()).isEqualTo("REVOGADA");
        assertThat(enviar(0, "PUT", caminho + "/decisao", decisao("APROVAR"), null).statusCode()).isEqualTo(409);
    }

    @Test
    void constraintsRejeitamEstadoIncoerenteSemAlterarAutorizacao() throws Exception {
        solicitar("constraint");
        assertThatThrownBy(() -> jdbc.sql("UPDATE escrituradora.autorizacao_consulta SET estado = 'ATIVA'").update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.sql("SELECT estado FROM escrituradora.autorizacao_consulta").query(String.class).single()).isEqualTo("PENDENTE");
    }

    private String solicitar(String chave) throws Exception {
        var resposta = enviar(2, "POST", BASE, entrada(), chave);
        assertThat(resposta.statusCode()).isEqualTo(201);
        return resposta.headers().firstValue("Location").orElseThrow();
    }

    private ObjectNode entrada() {
        return json.createObjectNode().put("cnpjSacador", "11222333000181").put("fimVigencia", AGORA.plusSeconds(60).toString());
    }

    private ObjectNode decisao(String decisao) { return json.createObjectNode().put("decisao", decisao); }

    private HttpResponse<String> enviar(int identidade, String metodo, String caminho, JsonNode corpo, String chave) throws Exception {
        int porta = ambiente.getRequiredProperty("local.server.port", Integer.class);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).timeout(Duration.ofSeconds(20));
        if (identidade >= 0) request.header("Authorization", "Bearer " + TOKENS.get(identidade));
        if (chave != null) request.header("Idempotency-Key", chave);
        if (corpo != null) request.header("Content-Type", "application/json");
        return HTTP.send(request.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<HttpResponse<String>> concorrer(List<Callable<HttpResponse<String>>> chamadas) throws Exception {
        var prontos = new CountDownLatch(chamadas.size());
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(chamadas.size())) {
            var tarefas = new ArrayList<Future<HttpResponse<String>>>();
            for (var chamada : chamadas) {
                tarefas.add(executor.submit(() -> {
                    prontos.countDown();
                    if (!largada.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Largada não liberada");
                    return chamada.call();
                }));
            }
            try { assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { largada.countDown(); }
            var respostas = new ArrayList<HttpResponse<String>>();
            for (var tarefa : tarefas) respostas.add(tarefa.get(30, TimeUnit.SECONDS));
            return respostas;
        }
    }

    private int contar() { return jdbc.sql("SELECT count(*) FROM escrituradora.autorizacao_consulta").query(Integer.class).single(); }

    private void cadastrar(UUID id, String cnpj) {
        jdbc.sql("""
                INSERT INTO escrituradora.sacador (id, cnpj, nome, domicilio, inicio_operacao, habilitado)
                VALUES (:id, :cnpj, 'Sacador de teste', CAST(:domicilio AS jsonb), DATE '2020-01-01', true)
                """).param("id", id).param("cnpj", cnpj)
                .param("domicilio", json.writeValueAsString(ExemploEscrituracao.endereco())).update();
    }

    static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracaoRelogio {
        @Bean @Primary RelogioControlavel relogioControlavel() { return new RelogioControlavel(); }
    }

    static final class RelogioControlavel extends Clock {
        final AtomicReference<Instant> instante = new AtomicReference<>(AGORA);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instante.get(); }
    }
}
