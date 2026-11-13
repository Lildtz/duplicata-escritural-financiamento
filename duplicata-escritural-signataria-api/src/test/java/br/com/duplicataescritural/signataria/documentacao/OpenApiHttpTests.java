package br.com.duplicataescritural.signataria.documentacao;

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
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiHttpTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    private static final String ESCRITURACAO = "/api/v1/escrituracoes/manuais";
    private static final String PROCESSAMENTO = "/api/v1/escrituracoes/processamentos/{idProcessamento}";
    private static final String AUTORIZACOES = "/api/v1/autorizacoes-consulta";
    private static final String AUTORIZACAO = AUTORIZACOES + "/{id}";
    private static final UUID SACADOR = UUID.randomUUID();
    private static final String TOKEN_SACADOR = token();
    private static final String TOKEN_FINANCIADOR = token();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @Autowired Environment ambiente;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;

    @DynamicPropertySource
    static void identidades(DynamicPropertyRegistry propriedades) {
        propriedades.add("signataria.autenticacao-local.tokens[0].sha256", () -> digest(TOKEN_SACADOR));
        propriedades.add("signataria.autenticacao-local.tokens[0].sacador-id", SACADOR::toString);
        propriedades.add("signataria.autenticacao-local.tokens[1].sha256", () -> digest(TOKEN_FINANCIADOR));
        propriedades.add("signataria.autenticacao-local.tokens[1].financiador-id", () -> SACADOR.toString());
    }

    @Test
    void geraMetadadosSeteOperacoesEBearerOpacoSemActuatorOuParametrosInternos() throws Exception {
        var doc = documento();
        assertThat(doc.get("openapi").asString()).startsWith("3.0.");
        assertThat(doc.at("/info/title").asString()).isEqualTo("Signatária — Duplicata Escritural");
        assertThat(doc.at("/info/version").asString()).isEqualTo("v1");
        var esquema = doc.at("/components/securitySchemes/BearerLocal");
        assertThat(esquema.get("type").asString()).isEqualTo("http");
        assertThat(esquema.get("scheme").asString()).isEqualTo("bearer");
        assertThat(esquema.has("bearerFormat")).isFalse();
        assertThat(esquema.get("description").asString()).contains("Token opaco", "não é JWT");
        var operacoes = Map.of(ESCRITURACAO, List.of("post"), PROCESSAMENTO, List.of("get"), AUTORIZACOES, List.of("post"),
                AUTORIZACAO, List.of("get", "delete"), AUTORIZACAO + "/decisao", List.of("put"), AUTORIZACAO + "/duplicatas", List.of("get"));
        assertThat(nomes(doc.get("paths"))).containsExactlyInAnyOrderElementsOf(operacoes.keySet());
        var grupos = new java.util.HashSet<String>();
        for (var caminho : operacoes.entrySet()) {
            assertThat(nomes(doc.get("paths").get(caminho.getKey()))).containsExactlyInAnyOrderElementsOf(caminho.getValue());
            for (String metodo : caminho.getValue()) {
                var operacao = operacao(doc, caminho.getKey(), metodo);
                grupos.addAll(textos(operacao.get("tags")));
                assertThat(operacao.at("/security/0/BearerLocal").isArray()).isTrue();
                assertThat(operacao.get("description").asString()).isNotBlank();
                assertThat(operacao.toString()).doesNotContain("SacadorAutenticado", "ParticipanteAutenticado", "FinanciadorAutenticado", "parametros");
            }
        }
        assertThat(grupos).containsExactlyInAnyOrder("Escrituração Manual", "Processamentos", "Autorizações de consulta", "Consulta de Duplicatas");
        assertThat(doc.toString()).doesNotContain(TOKEN_SACADOR, TOKEN_FINANCIADOR, digest(TOKEN_SACADOR), digest(TOKEN_FINANCIADOR));
    }

    @Test
    void documentaCorposObrigatoriosIdempotenciaPaginacaoEHeaders() throws Exception {
        var doc = documento();
        for (String caminho : List.of(ESCRITURACAO, AUTORIZACOES)) {
            var op = operacao(doc, caminho, "post");
            assertThat(op.at("/requestBody/required").asBoolean()).isTrue();
            var chave = parametro(op, "Idempotency-Key");
            assertThat(chave.get("in").asString()).isEqualTo("header");
            assertThat(chave.get("required").asBoolean()).isTrue();
            assertThat(chave.at("/schema/minLength").asInt()).isEqualTo(1);
            assertThat(chave.at("/schema/maxLength").asInt()).isEqualTo(128);
            assertThat(chave.at("/schema/pattern").asString()).isEqualTo("[A-Za-z0-9._:-]+");
            String sucesso = caminho.equals(ESCRITURACAO) ? "202" : "201";
            assertThat(op.get("responses").get(sucesso).at("/headers/Location/schema/type").asString()).isEqualTo("string");
        }
        var duplicatas = operacao(doc, AUTORIZACAO + "/duplicatas", "get");
        assertThat(duplicatas.get("parameters").size()).isEqualTo(3);
        var pagina = parametro(duplicatas, "pagina");
        assertThat(pagina.at("/schema/minimum").asInt()).isZero();
        assertThat(pagina.at("/schema/default").asInt()).isZero();
        var tamanho = parametro(duplicatas, "tamanho");
        assertThat(tamanho.at("/schema/minimum").asInt()).isEqualTo(1);
        assertThat(tamanho.at("/schema/maximum").asInt()).isEqualTo(100);
        assertThat(tamanho.at("/schema/default").asInt()).isEqualTo(50);
        assertThat(parametro(duplicatas, "id").at("/schema/format").asString()).isEqualTo("uuid");
        assertThat(operacao(doc, AUTORIZACAO + "/decisao", "put").at("/requestBody/required").asBoolean()).isTrue();
    }

    @Test
    void schemasRepresentamDtosReaisPrecisaoEnumsECamposOpcionais() throws Exception {
        var schemas = documento().at("/components/schemas");
        assertThat(textos(schemas.get("SolicitacaoEscrituracaoRequest").get("required"))).containsExactlyInAnyOrder(
                "sacado", "tipoDuplicata", "numeroDuplicata", "numeroFatura", "numeroParcela", "dataFaturamento",
                "dataVencimento", "valorFatura", "valorDuplicata", "documentoFiscal");
        assertThat(textos(schemas.get("SacadoRequest").get("required"))).containsExactlyInAnyOrder("cnpj", "nome", "domicilio");
        assertThat(textos(schemas.get("EnderecoRequest").get("required"))).containsExactlyInAnyOrder("logradouro", "numero", "bairro", "cep", "municipioIbge", "uf");
        assertThat(textos(schemas.get("DocumentoFiscalRequest").get("required"))).containsExactlyInAnyOrder("tipo", "numero", "serie");
        assertThat(schemas.get("SolicitacaoEscrituracaoRequest").get("additionalProperties").asBoolean()).isFalse();
        assertThat(schemas.get("SacadoRequest").at("/properties/cnpj/example").asString()).isEqualTo("12.ABC.345/01DE-35");
        assertThat(textos(schemas.get("SolicitacaoAutorizacao").get("required"))).containsExactlyInAnyOrder("cnpjSacador", "fimVigencia");
        assertThat(textos(schemas.get("DecisaoAutorizacao").at("/properties/decisao/enum"))).containsExactlyInAnyOrder("APROVAR", "RECUSAR");
        var autorizacao = schemas.get("RespostaAutorizacao");
        assertThat(textos(autorizacao.at("/properties/status/enum"))).containsExactlyInAnyOrder("PENDENTE", "ATIVA", "RECUSADA", "REVOGADA");
        assertThat(textos(autorizacao.get("required"))).contains("idAutorizacao", "financiadorId", "status", "vigente", "fimVigencia")
                .doesNotContain("inicioVigencia", "decididaEm", "revogadaEm", "revogadoPor");
        assertThat(autorizacao.at("/properties/fimVigencia/format").asString()).isEqualTo("date-time");
        var processamento = schemas.get("RespostaProcessamento");
        assertThat(textos(processamento.at("/properties/status/enum"))).containsExactlyInAnyOrder(
                "PENDENTE", "EM_PROCESSAMENTO", "CONCLUIDO", "REJEITADO", "FALHA_TECNICA");
        assertThat(textos(processamento.get("required"))).containsExactlyInAnyOrder("idProcessamento", "status", "recebidoEm");
        assertThat(textos(schemas.get("RespostaDuplicatas").get("required"))).hasSize(6);
        assertThat(textos(schemas.get("RespostaDuplicatas").at("/properties/escopo/enum"))).containsExactly("LOCAL_SIMULADO");
        var titulo = schemas.get("DuplicataConsultada");
        assertThat(nomes(titulo.get("properties"))).containsExactlyInAnyOrder(
                "idDuplicata", "numeroDuplicata", "cnpjSacado", "dataVencimento", "valorNominal", "escrituradaEm");
        assertThat(textos(titulo.get("required"))).hasSize(6);
        assertThat(titulo.at("/properties/dataVencimento/format").asString()).isEqualTo("date");
        assertThat(titulo.at("/properties/escrituradaEm/format").asString()).isEqualTo("date-time");
        assertThat(titulo.at("/properties/valorNominal/type").asString()).isEqualTo("number");
        assertThat(titulo.at("/properties/valorNominal/multipleOf").asString()).isEqualTo("0.01");
        assertThat(new java.math.BigDecimal(titulo.at("/properties/valorNominal/maximum").asString())).isEqualByComparingTo("999999999999.99");
        assertThat(titulo.at("/properties/cnpjSacado/pattern").asString()).isEqualTo("[A-Z0-9]{12}[0-9]{2}");
    }

    @Test
    void respostasDocumentadasPreservamSucessosEErrosDistintos() throws Exception {
        var doc = documento();
        for (String caminho : nomes(doc.get("paths"))) {
            for (String metodo : nomes(doc.get("paths").get(caminho))) {
                var respostas = operacao(doc, caminho, metodo).get("responses");
                var esperadas = new ArrayList<>(List.of("401", "403", "500", "503"));
                if (!caminho.equals(PROCESSAMENTO)) esperadas.add("400");
                if (!caminho.equals(ESCRITURACAO)) esperadas.add("404");
                esperadas.add(caminho.equals(ESCRITURACAO) ? "202" : metodo.equals("post") ? "201" : metodo.equals("delete") ? "204" : "200");
                if (!metodo.equals("get")) esperadas.add("409");
                if (metodo.equals("post") || metodo.equals("put") || caminho.equals(PROCESSAMENTO)) esperadas.add("422");
                assertThat(nomes(respostas)).as(caminho + " " + metodo).containsExactlyInAnyOrderElementsOf(esperadas);
            }
        }
        assertThat(doc.at("/components/responses/NaoAutenticado/headers/WWW-Authenticate/schema/example").asString()).isEqualTo("Bearer");
        assertThat(doc.at("/components/schemas/ErroSeguranca/additionalProperties").asBoolean()).isFalse();
        assertThat(nomes(doc.at("/components/schemas/ErroSeguranca/properties"))).containsExactlyInAnyOrder("status", "title");
        assertThat(doc.at("/components/schemas/ProblemaAutorizacao/properties").has("codigo")).isTrue();
        assertThat(doc.at("/components/schemas/ProblemaEscrituracao/properties").has("codigo")).isFalse();
        assertThat(doc.at("/components/schemas/ProblemaEscrituracao/properties/instance/format").asString()).isEqualTo("uri-reference");
        assertThat(operacao(doc, PROCESSAMENTO, "get").at("/responses/422/$ref").asString()).endsWith("/DadosEscrituracaoInvalidos");
        assertThat(operacao(doc, AUTORIZACAO, "delete").at("/responses/204").has("content")).isFalse();
    }

    @Test
    void serveUiConfiguracaoERecursosSemCredencialNoPerfilLocal() throws Exception {
        var entrada = enviar("/swagger-ui.html", null, "GET");
        assertThat(entrada.statusCode()).isBetween(300, 399);
        assertThat(enviar(entrada.headers().firstValue("Location").orElseThrow(), null, "GET").statusCode()).isEqualTo(200);
        for (String recurso : List.of("swagger-ui.css", "swagger-ui-bundle.js", "swagger-ui-standalone-preset.js", "swagger-initializer.js")) {
            assertThat(enviar("/swagger-ui/" + recurso, null, "GET").statusCode()).as(recurso).isEqualTo(200);
        }
        var configuracao = enviar("/v3/api-docs/swagger-config", null, "GET");
        assertThat(configuracao.statusCode()).isEqualTo(200);
        assertThat(json.readTree(configuracao.body()).get("url").asString()).isEqualTo("/v3/api-docs");
    }

    @Test
    void excecaoDocumentalNaoLiberaNegocioNemPermiteTrocaDePapeis() throws Exception {
        jdbc.sql("INSERT INTO escrituradora.sacador (id,cnpj,nome,domicilio,inicio_operacao,habilitado) "
                + "VALUES (:id,'11222333000181','Sacador fictício',"
                + "'{\"logradouro\":\"Rua Teste\",\"numero\":\"1\",\"bairro\":\"Centro\",\"cep\":\"01001000\","
                + "\"municipioIbge\":\"3550308\",\"uf\":\"SP\"}','2020-01-01',true)")
                .param("id", SACADOR).update();
        var respostas = documento().at("/components/responses");
        for (String credencial : java.util.Arrays.asList(null, token())) {
            var naoAutenticado = enviar(ESCRITURACAO, credencial, "POST");
            assertThat(naoAutenticado.statusCode()).isEqualTo(401);
            assertThat(naoAutenticado.headers().firstValue("WWW-Authenticate")).contains("Bearer");
            assertThat(nomes(json.readTree(naoAutenticado.body()))).containsExactlyInAnyOrder("status", "title");
            conferirContentType(naoAutenticado, respostas, "NaoAutenticado");
        }
        assertThat(enviar(AUTORIZACOES + "/" + UUID.randomUUID(), null, "GET").statusCode()).isEqualTo(401);
        var papelInadequado = enviar(ESCRITURACAO, TOKEN_FINANCIADOR, "POST");
        assertThat(papelInadequado.statusCode()).isEqualTo(403);
        assertThat(papelInadequado.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(nomes(json.readTree(papelInadequado.body()))).containsExactlyInAnyOrder("status", "title");
        conferirContentType(papelInadequado, respostas, "PapelNegado");
        assertThat(enviar(AUTORIZACOES, TOKEN_SACADOR, "POST").statusCode()).isEqualTo(403);
        assertThat(enviar(AUTORIZACOES + "/" + UUID.randomUUID() + "/duplicatas", TOKEN_SACADOR, "GET").statusCode()).isEqualTo(403);
        assertThat(enviar(AUTORIZACOES + "/" + UUID.randomUUID() + "/decisao", TOKEN_FINANCIADOR, "PUT").statusCode()).isEqualTo(403);
        var solicitada = enviar(AUTORIZACOES, TOKEN_FINANCIADOR, "POST",
                Map.of("cnpjSacador", "11222333000181", "fimVigencia", java.time.Instant.now().plusSeconds(60)), "headers-seguranca");
        assertThat(solicitada.statusCode()).isEqualTo(201);
        assertThat(json.readTree(solicitada.body()).get("status").asString()).isEqualTo("PENDENTE");
        String id = json.readTree(solicitada.body()).get("idAutorizacao").asString();
        var consultaNegada = enviar(AUTORIZACOES + "/" + id + "/duplicatas", TOKEN_FINANCIADOR, "GET");
        assertThat(consultaNegada.statusCode()).isEqualTo(403);
        assertThat(consultaNegada.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(json.readTree(consultaNegada.body()).get("codigo").asString()).isEqualTo("CONSULTA_NAO_PERMITIDA");
        assertThat(nomes(json.readTree(consultaNegada.body()))).contains("status", "title", "detail", "instance", "codigo");
        conferirContentType(consultaNegada, respostas, "AcessoAutorizacaoNegado");
        var uuidInvalido = enviar("/api/v1/escrituracoes/processamentos/invalido", TOKEN_SACADOR, "GET");
        assertThat(uuidInvalido.statusCode()).isEqualTo(422);
        assertThat(nomes(json.readTree(uuidInvalido.body()))).contains("title", "status", "detail", "instance").doesNotContain("codigo");
        assertThat(enviar("/v3/api-docs", null, "POST").statusCode()).isNotEqualTo(200);
        assertThat(enviar("/actuator/env", null, "GET").statusCode()).isNotEqualTo(200);
    }

    private JsonNode documento() throws Exception {
        var resposta = enviar("/v3/api-docs", null, "GET");
        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        return json.readTree(resposta.body());
    }

    private JsonNode operacao(JsonNode doc, String caminho, String metodo) {
        return doc.get("paths").get(caminho).get(metodo);
    }

    private JsonNode parametro(JsonNode operacao, String nome) {
        for (var parametro : operacao.get("parameters")) if (parametro.get("name").asString().equals(nome)) return parametro;
        throw new AssertionError("Parâmetro não documentado: " + nome);
    }

    private static List<String> nomes(JsonNode objeto) {
        var nomes = new ArrayList<String>();
        objeto.properties().forEach(p -> nomes.add(p.getKey()));
        return nomes;
    }

    private static List<String> textos(JsonNode array) {
        var valores = new ArrayList<String>();
        array.forEach(valor -> valores.add(valor.asString()));
        return valores;
    }

    private HttpResponse<String> enviar(String caminho, String token, String metodo) throws Exception {
        return enviar(caminho, token, metodo, Map.of(), null);
    }

    private HttpResponse<String> enviar(String caminho, String token, String metodo, Object corpo, String chave) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + ambiente.getProperty("local.server.port") + caminho))
                .timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (chave != null) request.header("Idempotency-Key", chave);
        if (!metodo.equals("GET")) request.header("Content-Type", "application/json");
        return HTTP.send(request.method(metodo, metodo.equals("GET") ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void conferirContentType(HttpResponse<String> resposta, JsonNode respostas, String componente) {
        String header = resposta.headers().firstValue("Content-Type").orElseThrow();
        var tipo = org.springframework.http.MediaType.parseMediaType(header);
        String mediaType = tipo.getType() + "/" + tipo.getSubtype();
        assertThat(mediaType).isEqualTo("application/problem+json");
        assertThat(nomes(respostas.get(componente).get("content"))).containsExactly(mediaType);
        System.out.printf("Contrato %s: HTTP %d; Content-Type=%s; WWW-Authenticate=%s%n", componente,
                resposta.statusCode(), header, resposta.headers().firstValue("WWW-Authenticate").orElse("ausente"));
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
