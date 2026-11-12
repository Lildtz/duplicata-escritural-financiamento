package br.com.duplicataescritural.signataria.escrituracao.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import br.com.duplicataescritural.signataria.escrituracao.aplicacao.ServicoProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ExemploEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.ProcessamentoEscrituracaoRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "signataria.escrituracao.processamento.habilitado=false")
@ExtendWith(OutputCaptureExtension.class)
class EscrituracaoHttpTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID SACADOR_A = UUID.randomUUID();
    private static final UUID SACADOR_B = UUID.randomUUID();
    private static final UUID SACADOR_DESABILITADO = UUID.randomUUID();
    private static final List<UUID> SACADORES = List.of(SACADOR_A, SACADOR_B, SACADOR_DESABILITADO, UUID.randomUUID());
    private static final List<String> TOKENS = List.of(token(), token(), token(), token(), token());
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String POST = "/api/v1/escrituracoes/manuais";

    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;
    @Autowired Environment environment;
    @Autowired ProcessamentoEscrituracaoRepository processamentos;
    @Autowired Flyway flyway;
    @Autowired ServicoProcessamentoEscrituracao worker;

    @DynamicPropertySource
    static void tokensLocais(DynamicPropertyRegistry registry) {
        for (int i = 0; i < TOKENS.size(); i++) {
            int indice = i;
            registry.add("signataria.autenticacao-local.tokens[" + i + "].sha256", () -> digest(TOKENS.get(indice)));
            registry.add("signataria.autenticacao-local.tokens[" + i + "].sacador-id",
                    () -> indice == 4 ? SACADOR_A.toString() : SACADORES.get(indice).toString());
        }
    }

    @BeforeEach
    void cadastrarSacadoresNoBancoDescartavel() {
        jdbc.sql("DELETE FROM escrituradora.processamento_escrituracao").update();
        jdbc.sql("DELETE FROM escrituradora.duplicata").update();
        jdbc.sql("DELETE FROM escrituradora.sacador").update();
        cadastrar(SACADOR_A, "11222333000181", true);
        cadastrar(SACADOR_B, "12ABC34501DE35", true);
        cadastrar(SACADOR_DESABILITADO, "11444777000161", false);
    }

    @Test
    void recebeEConsultaProcessamentoAposCommit() throws Exception {
        var resposta = post(TOKENS.getFirst(), "recebimento", entrada());
        assertThat(resposta.statusCode()).isEqualTo(202);
        var corpo = json.readTree(resposta.body());
        UUID id = UUID.fromString(corpo.get("idProcessamento").asString());
        assertThat(corpo.get("status").asString()).isEqualTo("PENDENTE");
        assertThat(corpo.hasNonNull("recebidoEm")).isTrue();
        String location = resposta.headers().firstValue("Location").orElseThrow();
        assertThat(location).isEqualTo("/api/v1/escrituracoes/processamentos/" + id);
        assertThat(get(TOKENS.getFirst(), location).body()).isEqualTo(resposta.body());
        var persistido = processamentos.findByIdAndSacadorId(id, SACADOR_A).orElseThrow();
        assertThat(persistido.dados()).isEqualTo(ExemploEscrituracao.dados());
        assertThat(persistido.sacadorNoRecebimento().cnpj()).isEqualTo(new Cnpj("11222333000181"));
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void normalizaConteudoEReconheceRepeticaoSemAlterarFotografia() throws Exception {
        var primeira = post(TOKENS.getFirst(), "equivalente", entrada());
        var equivalente = entrada();
        equivalente.put("numeroDuplicata", " DUP-1 ");
        equivalente.put("valorFatura", 100);
        equivalente.put("valorDuplicata", 100);
        ((ObjectNode) equivalente.get("sacado")).put("cnpj", "12.abc.345/01de-35");
        ((ObjectNode) equivalente.get("documentoFiscal")).put("numero", "000000123").put("serie", "001");
        jdbc.sql("UPDATE escrituradora.sacador SET nome = 'Nome atualizado' WHERE id = :id").param("id", SACADOR_A).update();
        var repetida = post(TOKENS.getFirst(), "equivalente", equivalente);
        assertThat(repetida.statusCode()).isEqualTo(202);
        assertThat(repetida.body()).isEqualTo(primeira.body());
        var id = UUID.fromString(json.readTree(primeira.body()).get("idProcessamento").asString());
        assertThat(processamentos.findByIdAndSacadorId(id, SACADOR_A).orElseThrow().sacadorNoRecebimento().nome()).isEqualTo("Sacador local");
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void recusaPayloadConflitanteSemModificarOriginal() throws Exception {
        var primeira = post(TOKENS.getFirst(), "conflito", entrada());
        var outro = entrada().put("numeroDuplicata", "OUTRA");
        assertThat(post(TOKENS.getFirst(), "conflito", outro).statusCode()).isEqualTo(409);
        assertThat(post(TOKENS.getFirst(), "conflito", entrada()).body()).isEqualTo(primeira.body());
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void rejeitaPedidosSemChaveOuComChaveInvalida() throws Exception {
        assertThat(post(TOKENS.getFirst(), null, entrada()).statusCode()).isEqualTo(400);
        assertThat(post(TOKENS.getFirst(), "chave com espaços", entrada()).statusCode()).isEqualTo(400);
        assertThat(post(TOKENS.getFirst(), "x".repeat(129), entrada()).statusCode()).isEqualTo(400);
        assertThat(contar()).isZero();
    }

    @Test
    void protegeAmbasAsRotasEConservaActuator(CapturedOutput output) throws Exception {
        String consulta = "/api/v1/escrituracoes/processamentos/" + UUID.randomUUID();
        for (String credencial : List.of("", token(), TOKENS.get(3))) {
            assertThat(post(credencial, "autenticacao", entrada()).statusCode()).isEqualTo(401);
            assertThat(get(credencial, consulta).statusCode()).isEqualTo(401);
        }
        assertThat(get("", "/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get("", "/actuator/info").statusCode()).isEqualTo(200);
        assertThat(get("", "/actuator/env").statusCode()).isNotEqualTo(200);
        for (String credencial : TOKENS) {
            assertThat(output.getAll()).doesNotContain(credencial);
        }
        assertThat(contar()).isZero();
    }

    @Test
    void sacadorDesabilitadoNaoPodeSolicitarNemConsultar() throws Exception {
        assertThat(post(TOKENS.get(2), "desabilitado", entrada()).statusCode()).isEqualTo(403);
        assertThat(get(TOKENS.get(2), "/api/v1/escrituracoes/processamentos/" + UUID.randomUUID()).statusCode()).isEqualTo(403);
        assertThat(contar()).isZero();
    }

    @Test
    void isolaSacadoresEPermiteMesmaChaveEntreEles() throws Exception {
        var a = post(TOKENS.getFirst(), "mesma-chave", entrada());
        var b = post(TOKENS.get(1), "mesma-chave", entrada());
        assertThat(a.statusCode()).isEqualTo(202);
        assertThat(b.statusCode()).isEqualTo(202);
        assertThat(a.body()).isNotEqualTo(b.body());
        String location = a.headers().firstValue("Location").orElseThrow();
        assertThat(get(TOKENS.get(1), location).statusCode()).isEqualTo(404);
        assertThat(get(TOKENS.getFirst(), "/api/v1/escrituracoes/processamentos/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(contar()).isEqualTo(2);
    }

    @Test
    void rotacaoDeTokenPreservaIdempotencia() throws Exception {
        var a = post(TOKENS.getFirst(), "rotacao", entrada());
        var b = post(TOKENS.get(4), "rotacao", entrada());
        assertThat(a.statusCode()).isEqualTo(202);
        assertThat(b.body()).isEqualTo(a.body());
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void jsonNaoSelecionaIdentidadeERejeitaCamposDesconhecidos() throws Exception {
        assertThat(post(TOKENS.getFirst(), "identidade", entrada().put("sacadorCnpj", "12ABC34501DE35")).statusCode()).isEqualTo(400);
        assertThat(post(TOKENS.getFirst(), "campo", entrada().put("desconto", 10)).statusCode()).isEqualTo(400);
        assertThat(contar()).isZero();
    }

    @Test
    void rejeitaDadosInvalidosSemPersistir() throws Exception {
        assertThat(post(TOKENS.getFirst(), "valor", entrada().put("valorDuplicata", -1)).statusCode()).isEqualTo(422);
        assertThat(post(TOKENS.getFirst(), "periodo", entrada().put("dataFaturamento", "2019-01-01")).statusCode()).isEqualTo(422);
        assertThat(post(TOKENS.getFirst(), "parcela", entrada().put("numeroParcela", 2)).statusCode()).isEqualTo(422);
        var documentoInvalido = entrada();
        ((ObjectNode) documentoInvalido.get("sacado")).put("cnpj", "12ABC34501DE34");
        assertThat(post(TOKENS.getFirst(), "cnpj", documentoInvalido).statusCode()).isEqualTo(422);
        var incompleta = entrada();
        incompleta.remove("sacado");
        assertThat(post(TOKENS.getFirst(), "incompleta", incompleta).statusCode()).isEqualTo(422);
        assertThat(contar()).isZero();
    }

    @Test
    void referenciaFiscalNaoEhUnicaENaoCriaDuplicata() throws Exception {
        assertThat(post(TOKENS.getFirst(), "primeira", entrada()).statusCode()).isEqualTo(202);
        assertThat(post(TOKENS.getFirst(), "segunda", entrada()).statusCode()).isEqualTo(202);
        assertThat(contar()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM escrituradora.duplicata").query(Integer.class).single()).isZero();
    }

    @Test
    void concorrenciaNaMesmaChavePersisteUmProcessamento() throws Exception {
        var respostas = concorrentes(8, false);
        assertThat(respostas).allSatisfy(resposta -> assertThat(resposta.statusCode()).isEqualTo(202));
        assertThat(respostas.stream().map(HttpResponse::body).distinct().count()).isEqualTo(1);
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void concorrenciaComPayloadsConflitantesTemUmVencedor() throws Exception {
        var respostas = concorrentes(2, true);
        assertThat(respostas.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(202, 409);
        assertThat(contar()).isEqualTo(1);
    }

    @Test
    void preservaHistoricoFlywayAoMigrarNovamente() throws Exception {
        var resposta = post(TOKENS.getFirst(), "flyway", entrada());
        String location = resposta.headers().firstValue("Location").orElseThrow();
        var checksum = jdbc.sql("SELECT checksum FROM escrituradora.flyway_schema_history WHERE version = '1'")
                .query(Integer.class).single();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.sql("SELECT checksum FROM escrituradora.flyway_schema_history WHERE version = '1'")
                .query(Integer.class).single()).isEqualTo(checksum);
        assertThat(get(TOKENS.getFirst(), location).body()).isEqualTo(resposta.body());
    }

    @Test
    void consultaConclusaoIsoladaERepetePostComRespostaOriginal() throws Exception {
        var original = post(TOKENS.getFirst(), "concluido", entrada());
        String location = original.headers().firstValue("Location").orElseThrow();
        worker.processarProximo();
        var consulta = get(TOKENS.getFirst(), location);
        assertThat(consulta.statusCode()).isEqualTo(200);
        var corpo = json.readTree(consulta.body());
        assertThat(corpo.get("status").asString()).isEqualTo("CONCLUIDO");
        UUID idDuplicata = UUID.fromString(corpo.get("idDuplicata").asString());
        assertThat(jdbc.sql("SELECT count(*) FROM escrituradora.duplicata WHERE id = :id")
                .param("id", idDuplicata).query(Integer.class).single()).isEqualTo(1);
        assertThat(corpo.hasNonNull("escrituradaEm")).isTrue();
        assertThat(corpo.hasNonNull("finalizadoEm")).isTrue();
        assertThat(corpo.has("codigo")).isFalse();
        assertThat(get(TOKENS.get(1), location).statusCode()).isEqualTo(404);
        var repetida = post(TOKENS.getFirst(), "concluido", entrada());
        assertThat(repetida.statusCode()).isEqualTo(202);
        assertThat(repetida.body()).isEqualTo(original.body());
        assertThat(repetida.headers().firstValue("Location")).contains(location);
        assertThat(post(TOKENS.getFirst(), "concluido", entrada().put("numeroDuplicata", "OUTRO")).statusCode()).isEqualTo(409);
        assertThat(get(TOKENS.getFirst(), location).body()).isEqualTo(consulta.body());
    }

    @Test
    void consultaRejeicaoDeNegocioComCodigoEstavel() throws Exception {
        post(TOKENS.getFirst(), "titulo-original", entrada());
        worker.processarProximo();
        var repetida = post(TOKENS.getFirst(), "titulo-repetido", entrada());
        worker.processarProximo();
        String location = repetida.headers().firstValue("Location").orElseThrow();
        var consulta = get(TOKENS.getFirst(), location);
        assertThat(consulta.statusCode()).isEqualTo(200);
        var corpo = json.readTree(consulta.body());
        assertThat(corpo.get("status").asString()).isEqualTo("REJEITADO");
        assertThat(corpo.get("codigo").asString()).isEqualTo("DUPLICATA_JA_ESCRITURADA");
        assertThat(corpo.hasNonNull("motivo")).isTrue();
        assertThat(corpo.hasNonNull("finalizadoEm")).isTrue();
        assertThat(corpo.has("idDuplicata")).isFalse();
        assertThat(corpo.has("escrituradaEm")).isFalse();
        assertThat(get(TOKENS.get(1), location).statusCode()).isEqualTo(404);
        assertThat(post(TOKENS.getFirst(), "titulo-repetido", entrada()).body()).isEqualTo(repetida.body());
    }

    @Test
    void consultaEmProcessamentoEFalhaTecnicaSemDetalhesInternos() throws Exception {
        var original = post(TOKENS.getFirst(), "falha-tecnica", entrada());
        String location = original.headers().firstValue("Location").orElseThrow();
        var reserva = worker.reservarProximo().orElseThrow();
        assertThat(json.readTree(get(TOKENS.getFirst(), location).body()).get("status").asString()).isEqualTo("EM_PROCESSAMENTO");
        worker.registrarFalha(reserva, new org.springframework.dao.NonTransientDataAccessResourceException(
                "detalhes-internos SQL escrituradora stacktrace"));
        var consulta = get(TOKENS.getFirst(), location);
        assertThat(consulta.statusCode()).isEqualTo(200);
        var corpo = json.readTree(consulta.body());
        assertThat(corpo.get("status").asString()).isEqualTo("FALHA_TECNICA");
        assertThat(corpo.get("codigo").asString()).isEqualTo("ERRO_TECNICO");
        assertThat(corpo.hasNonNull("motivo")).isTrue();
        assertThat(corpo.hasNonNull("finalizadoEm")).isTrue();
        assertThat(corpo.has("idDuplicata")).isFalse();
        assertThat(consulta.body()).doesNotContain("detalhes-internos", "SQL", "stacktrace", "escrituradora");
        assertThat(get(TOKENS.get(1), location).statusCode()).isEqualTo(404);
        assertThat(post(TOKENS.getFirst(), "falha-tecnica", entrada()).body()).isEqualTo(original.body());
    }

    private List<HttpResponse<String>> concorrentes(int quantidade, boolean conflito) throws Exception {
        var preparados = new CountDownLatch(quantidade);
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(quantidade)) {
            var futures = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < quantidade; i++) {
                int indice = i;
                futures.add(executor.submit(() -> {
                    preparados.countDown();
                    if (!largada.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Largada não liberada");
                    }
                    return post(TOKENS.getFirst(), "concorrente", entrada().put("numeroDuplicata",
                            conflito ? "DUP-" + indice : "DUP-1"));
                }));
            }
            try {
                assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                largada.countDown();
            }
            var respostas = new ArrayList<HttpResponse<String>>();
            for (var future : futures) {
                respostas.add(future.get(30, TimeUnit.SECONDS));
            }
            return respostas;
        }
    }

    private ObjectNode entrada() {
        return (ObjectNode) json.valueToTree(ExemploEscrituracao.dados());
    }

    private HttpResponse<String> post(String token, String chave, ObjectNode dados) throws Exception {
        var request = request(token, POST).header("Content-Type", "application/json");
        if (chave != null) {
            request.header("Idempotency-Key", chave);
        }
        return HTTP.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(dados))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String token, String caminho) throws Exception {
        return HTTP.send(request(token, caminho).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String token, String caminho) {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + caminho)).timeout(Duration.ofSeconds(20));
        if (!token.isEmpty()) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private int contar() {
        return jdbc.sql("SELECT count(*) FROM escrituradora.processamento_escrituracao").query(Integer.class).single();
    }

    private void cadastrar(UUID id, String cnpj, boolean habilitado) {
        jdbc.sql("""
                INSERT INTO escrituradora.sacador (id, cnpj, nome, domicilio, inicio_operacao, habilitado)
                VALUES (:id, :cnpj, 'Sacador local', CAST(:domicilio AS jsonb), DATE '2020-01-01', :habilitado)
                """).param("id", id).param("cnpj", cnpj)
                .param("domicilio", json.writeValueAsString(ExemploEscrituracao.endereco()))
                .param("habilitado", habilitado).update();
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
