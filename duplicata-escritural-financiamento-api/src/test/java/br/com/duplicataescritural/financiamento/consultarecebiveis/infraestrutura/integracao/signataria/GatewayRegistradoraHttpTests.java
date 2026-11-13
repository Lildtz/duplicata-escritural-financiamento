package br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.FalhaRegistradora;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.SolicitacaoAutorizacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria.ContratoSignataria.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GatewayRegistradoraHttpTests {
    private static final String AUTORIZACAO_PENDENTE = """
            {"idAutorizacao":"00000000-0000-0000-0000-000000000001",
             "financiadorId":"00000000-0000-0000-0000-000000000002", "cnpjSacador":"11222333000181",
             "status":"PENDENTE", "vigente":false, "solicitadaEm":"2030-01-01T00:00:00Z",
             "fimVigencia":"2030-01-02T00:00:00Z"}
            """;
    private final String token = token();
    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final GatewayRegistradoraHttp gateway = new GatewayRegistradoraHttp(builder.build());

    @AfterEach void verificaChamadasSemRetriesOuConsultasExtras() { servidor.verify(); }

    @Test
    void consultaAutorizacaoTipadaSemRecalcularEstadoOuVigencia() {
        servidor.expect(requestTo(BASE + CAMINHO)).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(withSuccess(AUTORIZACAO_ATIVA, MediaType.APPLICATION_JSON));
        var autorizacao = gateway.consultarAutorizacao(AUTORIZACAO);
        assertThat(autorizacao.status()).isEqualTo(AutorizacaoConsulta.Estado.ATIVA);
        assertThat(autorizacao.vigente()).isTrue();
        assertThat(autorizacao.financiadorId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(autorizacao.inicioVigencia()).isEqualTo(Instant.parse("2030-01-01T00:00:01Z"));
        assertThat(autorizacao.revogadaEm()).isNull();
    }

    @Test
    void solicitaERepeteAutorizacaoCom201MesmaChaveERecebimento() {
        var pedido = new SolicitacaoAutorizacao("11222333000181", Instant.parse("2030-01-02T00:00:00Z"), "pedido-1");
        servidor.expect(ExpectedCount.twice(), requestTo(BASE + "/api/v1/autorizacoes-consulta")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(header("Idempotency-Key", "pedido-1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"cnpjSacador":"11222333000181","fimVigencia":"2030-01-02T00:00:00Z"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(AUTORIZACAO_PENDENTE));
        var original = gateway.solicitarAutorizacao(pedido);
        assertThat(original.status()).isEqualTo(AutorizacaoConsulta.Estado.PENDENTE);
        assertThat(original.vigente()).isFalse();
        assertThat(gateway.solicitarAutorizacao(pedido)).isEqualTo(original);
    }

    @Test
    void postComTimeoutNaoRepeteAutomaticamenteEPermiteReenvioExplicitoComMesmaChave() {
        var pedido = new SolicitacaoAutorizacao("11222333000181", Instant.parse("2030-01-02T00:00:00Z"), "pedido-timeout");
        var chamadas = new AtomicInteger();
        servidor.expect(requestTo(BASE + "/api/v1/autorizacoes-consulta")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", pedido.chaveIdempotencia()))
                .andExpect(request -> chamadas.incrementAndGet())
                .andExpect(content().json("""
                        {"cnpjSacador":"11222333000181","fimVigencia":"2030-01-02T00:00:00Z"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withException(new SocketTimeoutException("Timeout após possível commit remoto")));
        servidor.expect(requestTo(BASE + "/api/v1/autorizacoes-consulta")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", pedido.chaveIdempotencia()))
                .andExpect(request -> chamadas.incrementAndGet())
                .andExpect(content().json("""
                        {"cnpjSacador":"11222333000181","fimVigencia":"2030-01-02T00:00:00Z"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(AUTORIZACAO_PENDENTE));

        assertThatThrownBy(() -> gateway.solicitarAutorizacao(pedido)).isInstanceOfSatisfying(FalhaRegistradora.class,
                falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.TIMEOUT));
        assertThat(chamadas.get()).isEqualTo(1);
        var repetida = gateway.solicitarAutorizacao(pedido);
        assertThat(repetida.idAutorizacao()).isEqualTo(AUTORIZACAO);
        assertThat(repetida.solicitadaEm()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(repetida.status()).isEqualTo(AutorizacaoConsulta.Estado.PENDENTE);
        assertThat(chamadas.get()).isEqualTo(2);
    }

    @Test
    void reutilizarChaveComConteudoDiferenteProduzConflitoSemRetry() {
        var pedido = new SolicitacaoAutorizacao("11222333000181", Instant.parse("2030-01-02T00:00:00Z"), "pedido-conflito");
        servidor.expect(requestTo(BASE + "/api/v1/autorizacoes-consulta")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", pedido.chaveIdempotencia()))
                .andExpect(jsonPath("$.fimVigencia").value("2030-01-02T00:00:00Z"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(AUTORIZACAO_PENDENTE));
        servidor.expect(requestTo(BASE + "/api/v1/autorizacoes-consulta")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", pedido.chaveIdempotencia()))
                .andExpect(jsonPath("$.fimVigencia").value("2030-01-02T00:00:01Z"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":409,\"codigo\":\"CONFLITO_IDEMPOTENCIA\"}"));

        assertThat(gateway.solicitarAutorizacao(pedido).idAutorizacao()).isEqualTo(AUTORIZACAO);
        var conflitante = new SolicitacaoAutorizacao(pedido.cnpjSacador(), pedido.fimVigencia().plusSeconds(1), pedido.chaveIdempotencia());
        assertThatThrownBy(() -> gateway.solicitarAutorizacao(conflitante)).isInstanceOfSatisfying(FalhaRegistradora.class,
                falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.CONFLITO));
    }

    @Test
    void preservaPaginasOrdemCamposPrecisaoDecimalEInstantes() {
        servidor.expect(requestTo(BASE + CAMINHO + "/duplicatas?pagina=0&tamanho=1"))
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(withSuccess(PAGINA, MediaType.APPLICATION_JSON));
        String segunda = PAGINA.replace("\"pagina\":0", "\"pagina\":1").replace("true", "false")
                .replace("DUP-01", "DUP-02").replace("999999999999.99", "0.01");
        servidor.expect(requestTo(BASE + CAMINHO + "/duplicatas?pagina=1&tamanho=1"))
                .andRespond(withSuccess(segunda, MediaType.APPLICATION_JSON));
        var primeira = gateway.consultarDuplicatas(AUTORIZACAO, new PaginacaoConsulta(0, 1));
        assertThat(primeira.cnpjSacador()).isEqualTo("11222333000181");
        assertThat(primeira.escopo().name()).isEqualTo("LOCAL_SIMULADO");
        assertThat(primeira.temProximaPagina()).isTrue();
        assertThat(primeira.itens()).hasSize(1);
        var titulo = primeira.itens().getFirst();
        assertThat(titulo.valorNominal()).isEqualByComparingTo("999999999999.99");
        assertThat(titulo.cnpjSacado()).isEqualTo("12ABC34501DE35");
        assertThat(titulo.dataVencimento().toString()).isEqualTo("2030-02-01");
        assertThat(titulo.escrituradaEm()).isEqualTo(Instant.parse("2030-01-01T00:00:01.123456Z"));
        var proxima = gateway.consultarDuplicatas(AUTORIZACAO, new PaginacaoConsulta(1, 1));
        assertThat(proxima.pagina()).isEqualTo(1);
        assertThat(proxima.tamanho()).isEqualTo(1);
        assertThat(proxima.temProximaPagina()).isFalse();
        assertThat(proxima.itens().getFirst().numeroDuplicata()).isEqualTo("DUP-02");
        assertThat(proxima.itens().getFirst().valorNominal()).isEqualByComparingTo("0.01");
    }

    @Test
    void listaVaziaSomenteQuandoRespostaRemotaValida() {
        servidor.expect(requestTo(BASE + CAMINHO + "/duplicatas?pagina=0&tamanho=1"))
                .andRespond(withSuccess("""
                        {"cnpjSacador":"11222333000181","escopo":"LOCAL_SIMULADO","itens":[],
                         "pagina":0,"tamanho":1,"temProximaPagina":false}
                        """, MediaType.APPLICATION_JSON));
        assertThat(gateway.consultarDuplicatas(AUTORIZACAO, new PaginacaoConsulta(0, 1)).itens()).isEmpty();
    }

    @ParameterizedTest(name = "Consulta rejeitada após {0}")
    @ValueSource(strings = {"revogação", "expiração"})
    void paginaSeguinteRejeitadaSemUsarStatusOuPaginaAnteriorComoAutorizacao(String motivo) {
        servidor.expect(requestTo(BASE + CAMINHO + "/duplicatas?pagina=0&tamanho=1"))
                .andRespond(withSuccess(PAGINA, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + CAMINHO + "/duplicatas?pagina=1&tamanho=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":403,\"codigo\":\"CONSULTA_NAO_PERMITIDA\",\"detail\":\"Consulta não permitida\"}"));
        gateway.consultarDuplicatas(AUTORIZACAO, new PaginacaoConsulta(0, 1));
        assertThatThrownBy(() -> gateway.consultarDuplicatas(AUTORIZACAO, new PaginacaoConsulta(1, 1)))
                .isInstanceOfSatisfying(FalhaRegistradora.class,
                        falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.ACESSO_NEGADO));
    }

    @ParameterizedTest
    @CsvSource({"401,AUTENTICACAO_REJEITADA", "403,ACESSO_NEGADO", "404,RECURSO_NAO_ENCONTRADO", "400,PARAMETROS_INVALIDOS",
            "422,PARAMETROS_INVALIDOS", "409,CONFLITO", "500,ERRO_REMOTO", "503,ERRO_REMOTO", "302,RESPOSTA_INCOMPATIVEL",
            "204,RESPOSTA_INCOMPATIVEL", "429,RESPOSTA_INCOMPATIVEL"})
    void classificaHttpSemExporCorpoCredencialOuCausa(int status, FalhaRegistradora.Motivo motivo) {
        servidor.expect(requestTo(BASE + CAMINHO + "/duplicatas?pagina=0&tamanho=1"))
                .andRespond(withRawStatus(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"detail\":\"SQL segredo " + token + "\"}"));
        assertThatThrownBy(() -> gateway.consultarDuplicatas(AUTORIZACAO, new PaginacaoConsulta(0, 1)))
                .isInstanceOfSatisfying(FalhaRegistradora.class, falha -> {
                    assertThat(falha.motivo()).isEqualTo(motivo);
                    assertThat(falha.getCause()).isNull();
                    assertThat(falha.getMessage()).doesNotContain(token, "SQL", "segredo", CAMINHO);
                });
    }

    @Test
    void naoDistingueAutorizacaoAlheiaDeInexistente() {
        var ids = new UUID[] {AUTORIZACAO, UUID.randomUUID()};
        for (UUID id : ids) {
            servidor.expect(requestTo(BASE + "/api/v1/autorizacoes-consulta/" + id))
                    .andRespond(withResourceNotFound());
        }
        for (UUID id : ids) {
            assertThatThrownBy(() -> gateway.consultarAutorizacao(id)).isInstanceOfSatisfying(FalhaRegistradora.class,
                    falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.RECURSO_NAO_ENCONTRADO));
        }
    }

    @ParameterizedTest
    @MethodSource("respostasInvalidas")
    void rejeitaRespostaIncompativelSemFallback(String corpo) {
        servidor.expect(requestTo(BASE + CAMINHO + "/duplicatas?pagina=0&tamanho=1"))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.consultarDuplicatas(AUTORIZACAO, new PaginacaoConsulta(0, 1)))
                .isInstanceOfSatisfying(FalhaRegistradora.class,
                        falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.RESPOSTA_INCOMPATIVEL));
    }

    static Stream<String> respostasInvalidas() {
        return Stream.of("", "{", "{}", "null", PAGINA.replace("LOCAL_SIMULADO", "GLOBAL"),
                PAGINA.replace("\"pagina\":0", "\"pagina\":1"), PAGINA.replace("\"tamanho\":1", "\"tamanho\":2"),
                PAGINA.replace("\"temProximaPagina\":true", "\"campoExtra\":true"),
                PAGINA.replace("999999999999.99", "null"), PAGINA.replace("2030-02-01", "data-inválida"),
                PAGINA.replace("\"itens\":[", "\"itens\":[null,"), PAGINA.replace("12ABC34501DE35", "12ABC345"));
    }

    @ParameterizedTest
    @MethodSource("autorizacoesInvalidas")
    void rejeitaAutorizacaoComCamposObrigatoriosAusentesOuIdentidadeIncompativel(String corpo) {
        servidor.expect(requestTo(BASE + CAMINHO)).andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.consultarAutorizacao(AUTORIZACAO)).isInstanceOfSatisfying(FalhaRegistradora.class,
                falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.RESPOSTA_INCOMPATIVEL));
    }

    static Stream<String> autorizacoesInvalidas() {
        return Stream.of(AUTORIZACAO_ATIVA.replace("\"vigente\":true", "\"extra\":true"),
                AUTORIZACAO_ATIVA.replace("\"idAutorizacao\":\"00000000-0000-0000-0000-000000000001\"", "\"extra\":true"),
                AUTORIZACAO_ATIVA.replace(AUTORIZACAO.toString(), UUID.randomUUID().toString()),
                AUTORIZACAO_ATIVA.replace("ATIVA", "ESTADO_DESCONHECIDO"));
    }

    @Test
    void rejeitaRespostaDeSolicitacaoComCnpjDiferenteDoPedido() {
        servidor.expect(requestTo(BASE + "/api/v1/autorizacoes-consulta"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body(AUTORIZACAO_ATIVA.replace("11222333000181", "12ABC34501DE35")));
        assertThatThrownBy(() -> gateway.solicitarAutorizacao(new SolicitacaoAutorizacao("11222333000181",
                Instant.parse("2030-01-02T00:00:00Z"), "chave"))).isInstanceOfSatisfying(FalhaRegistradora.class,
                falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.RESPOSTA_INCOMPATIVEL));
    }

    @Test
    void identificaFalhasDeConexaoSemVazarDetalhes() {
        servidor.expect(requestTo(BASE + CAMINHO)).andRespond(withException(new ConnectException("segredo")));
        assertThatThrownBy(() -> gateway.consultarAutorizacao(AUTORIZACAO)).isInstanceOfSatisfying(FalhaRegistradora.class,
                falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.INDISPONIVEL));
    }

    @ParameterizedTest
    @MethodSource("timeouts")
    void classificaTimeoutDeConexaoOuLeitura(IOException timeout) {
        servidor.expect(requestTo(BASE + CAMINHO)).andRespond(withException(timeout));
        assertThatThrownBy(() -> gateway.consultarAutorizacao(AUTORIZACAO)).isInstanceOfSatisfying(FalhaRegistradora.class,
                falha -> assertThat(falha.motivo()).isEqualTo(FalhaRegistradora.Motivo.TIMEOUT));
    }

    static Stream<IOException> timeouts() {
        return Stream.of(new HttpConnectTimeoutException("segredo"), new SocketTimeoutException("segredo"));
    }
}
