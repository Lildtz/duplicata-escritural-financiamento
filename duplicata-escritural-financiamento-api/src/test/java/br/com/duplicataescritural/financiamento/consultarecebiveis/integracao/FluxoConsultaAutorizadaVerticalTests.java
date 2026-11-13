package br.com.duplicataescritural.financiamento.consultarecebiveis.integracao;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.duplicataescritural.financiamento.FinanciamentoApplication;
import br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao.ServicoConsultaRecebiveis;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.DuplicataConsultada;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.FalhaRegistradora;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.FalhaRegistradora.Motivo;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginaDuplicatas;
import br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria.GatewayRegistradoraHttp;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.GatewayRegistradora;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static br.com.duplicataescritural.financiamento.consultarecebiveis.integracao.SignatariaReal.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers
class FluxoConsultaAutorizadaVerticalTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    static Path temporario;
    private static final String BASE = "/api/v1/autorizacoes-consulta";
    private static SignatariaReal signataria;
    private static ConfigurableApplicationContext banco;
    private static ServicoConsultaRecebiveis consultas;
    private static final List<DuplicataConsultada> ESCRITURADAS = new ArrayList<>();

    @BeforeAll
    static void iniciarAplicacoesEEscriturar() throws Exception {
        signataria = new SignatariaReal(temporario.resolve("signataria.log"));
        signataria.iniciar(POSTGRES);
        banco = banco(TOKEN_FINANCIADOR);
        consultas = banco.getBean(ServicoConsultaRecebiveis.class);
        assertThat(banco.getBean(GatewayRegistradora.class)).isInstanceOf(GatewayRegistradoraHttp.class);
        for (String valor : List.of("100.01", "999999999999.99", "0.01")) {
            ESCRITURADAS.add(escriturar(TOKEN_SACADOR, "DUP-" + ESCRITURADAS.size(), valor));
        }
        escriturar(TOKEN_OUTRO_SACADOR, "DUP-OUTRO", "50.00");
        ESCRITURADAS.sort(Comparator.comparing(DuplicataConsultada::escrituradaEm)
                .thenComparing(d -> d.idDuplicata().toString()));
    }

    @AfterAll
    static void encerrar() throws Exception {
        try {
            if (banco != null) banco.close();
        } finally {
            if (signataria != null) signataria.close();
        }
    }

    @Test
    void escrituraAutorizaConsultaPaginasERevalidaAposRevogacao() throws Exception {
        var autorizacao = solicitar(CNPJ, "fluxo");
        assertThat(autorizacao.status()).isEqualTo(AutorizacaoConsulta.Estado.PENDENTE);
        assertThat(autorizacao.vigente()).isFalse();
        assertThat(autorizacao.financiadorId()).isEqualTo(FINANCIADOR);
        falha(() -> consultas.consultarDuplicatas(autorizacao.idAutorizacao()), Motivo.ACESSO_NEGADO);

        decidir(autorizacao, TOKEN_SACADOR, "APROVAR");
        var ativa = consultas.consultarAutorizacao(autorizacao.idAutorizacao());
        assertThat(ativa.status()).isEqualTo(AutorizacaoConsulta.Estado.ATIVA);
        assertThat(ativa.vigente()).isTrue();
        assertThat(ativa.financiadorId()).isEqualTo(FINANCIADOR);

        Instant inicio = Instant.now();
        var consulta = consultas.consultarDuplicatas(ativa.idAutorizacao(), 0, 2);
        assertThat(consulta.consultadaEm()).isBetween(inicio, Instant.now());
        var primeira = consulta.resultado();
        assertThat(primeira.cnpjSacador()).isEqualTo(CNPJ);
        assertThat(primeira.escopo()).isEqualTo(PaginaDuplicatas.Escopo.LOCAL_SIMULADO);
        assertThat(primeira.pagina()).isZero();
        assertThat(primeira.tamanho()).isEqualTo(2);
        assertThat(primeira.temProximaPagina()).isTrue();
        assertThat(primeira.itens()).containsExactlyElementsOf(ESCRITURADAS.subList(0, 2));
        assertThat(consultas.consultarDuplicatas(ativa.idAutorizacao(), 0, 2).resultado()).isEqualTo(primeira);
        var segunda = consultas.consultarDuplicatas(ativa.idAutorizacao(), 1, 2).resultado();
        assertThat(segunda.pagina()).isEqualTo(1);
        assertThat(segunda.tamanho()).isEqualTo(2);
        assertThat(segunda.temProximaPagina()).isFalse();
        assertThat(segunda.itens()).containsExactly(ESCRITURADAS.get(2));
        assertThat(consultas.consultarDuplicatas(ativa.idAutorizacao(), 2, 2).resultado().itens()).isEmpty();
        assertThat(consultas.consultarDuplicatas(ativa.idAutorizacao()).resultado().itens())
                .containsExactlyElementsOf(ESCRITURADAS);

        // Nova sequência de páginas: o DELETE já retornou após o commit da revogação.
        consultas.consultarDuplicatas(ativa.idAutorizacao(), 0, 2);
        assertThat(signataria.enviar(TOKEN_SACADOR, "DELETE", caminho(ativa), null, null).statusCode()).isEqualTo(204);
        assertThat(consultas.consultarAutorizacao(ativa.idAutorizacao()).status()).isEqualTo(AutorizacaoConsulta.Estado.REVOGADA);
        falha(() -> consultas.consultarDuplicatas(ativa.idAutorizacao(), 1, 2), Motivo.ACESSO_NEGADO);
    }

    @Test
    void isolaFinanciadoresEPapeisSemEnumerarAutorizacoes() throws Exception {
        var propria = solicitar(CNPJ, "isolamento");
        assertThat(signataria.enviar(TOKEN_OUTRO_SACADOR, "PUT", caminho(propria) + "/decisao",
                Map.of("decisao", "APROVAR"), null).statusCode()).isEqualTo(404);
        assertThat(consultas.consultarAutorizacao(propria.idAutorizacao()).status()).isEqualTo(AutorizacaoConsulta.Estado.PENDENTE);
        decidir(propria, TOKEN_SACADOR, "APROVAR");
        try (var outroBanco = banco(TOKEN_OUTRO_FINANCIADOR)) {
            var outro = outroBanco.getBean(ServicoConsultaRecebiveis.class);
            var alheia = outro.solicitarAutorizacao(CNPJ, fim(), "isolamento");
            assertThat(alheia.financiadorId()).isEqualTo(OUTRO_FINANCIADOR);
            assertThat(alheia.idAutorizacao()).isNotEqualTo(propria.idAutorizacao());
            falha(() -> outro.consultarAutorizacao(propria.idAutorizacao()), Motivo.RECURSO_NAO_ENCONTRADO);
            falha(() -> outro.consultarDuplicatas(propria.idAutorizacao()), Motivo.RECURSO_NAO_ENCONTRADO);
            falha(() -> consultas.consultarDuplicatas(alheia.idAutorizacao()), Motivo.RECURSO_NAO_ENCONTRADO);
            falha(() -> outro.consultarDuplicatas(UUID.randomUUID()), Motivo.RECURSO_NAO_ENCONTRADO);
        }
        var oculta = signataria.enviar(TOKEN_OUTRO_FINANCIADOR, "GET", caminho(propria) + "/duplicatas", null, null);
        var ausente = signataria.enviar(TOKEN_OUTRO_FINANCIADOR, "GET", BASE + "/" + UUID.randomUUID() + "/duplicatas", null, null);
        assertThat(oculta.statusCode()).isEqualTo(404);
        assertThat(ausente.statusCode()).isEqualTo(404);
        // O Problem Detail ecoa o caminho pedido em instance; a semântica de erro é a mesma.
        for (String campo : List.of("status", "codigo", "title", "detail")) {
            assertThat(signataria.json.readTree(oculta.body()).get(campo))
                    .isEqualTo(signataria.json.readTree(ausente.body()).get(campo));
        }
        assertThat(signataria.enviar(TOKEN_SACADOR, "GET", caminho(propria) + "/duplicatas", null, null).statusCode()).isEqualTo(403);
        try (var invalido = banco(TOKEN_INVALIDO)) {
            falha(() -> invalido.getBean(ServicoConsultaRecebiveis.class).consultarDuplicatas(propria.idAutorizacao()),
                    Motivo.AUTENTICACAO_REJEITADA);
        }
    }

    @Test
    void expiracaoRealBloqueiaNovaConsultaSemModificarEstado() throws Exception {
        // Quatro chamadas de até 5s precisam caber antes da expiração, com mais 10s de margem.
        Instant fim = Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.MICROS);
        var autorizacao = consultas.solicitarAutorizacao(CNPJ, fim, "expiracao");
        decidir(autorizacao, TOKEN_SACADOR, "APROVAR");
        assertThat(consultas.consultarAutorizacao(autorizacao.idAutorizacao()).vigente()).isTrue();
        assertThat(consultas.consultarDuplicatas(autorizacao.idAutorizacao()).resultado().itens()).isNotEmpty();
        await().alias("Expiração observada pela Signatária").atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
                    var expirada = consultas.consultarAutorizacao(autorizacao.idAutorizacao());
                    assertThat(expirada.status()).isEqualTo(AutorizacaoConsulta.Estado.ATIVA);
                    assertThat(expirada.vigente()).isFalse();
                });
        falha(() -> consultas.consultarDuplicatas(autorizacao.idAutorizacao()), Motivo.ACESSO_NEGADO);
    }

    @Test
    void retornaVazioSomenteComAutorizacaoValidaESemTitulosCorrespondentes() throws Exception {
        var autorizacao = solicitar(CNPJ_SEM_TITULOS, "vazia");
        falha(() -> consultas.consultarDuplicatas(autorizacao.idAutorizacao()), Motivo.ACESSO_NEGADO);
        decidir(autorizacao, TOKEN_SACADOR_SEM_TITULOS, "APROVAR");
        var pagina = consultas.consultarDuplicatas(autorizacao.idAutorizacao()).resultado();
        assertThat(pagina.cnpjSacador()).isEqualTo(CNPJ_SEM_TITULOS);
        assertThat(pagina.escopo()).isEqualTo(PaginaDuplicatas.Escopo.LOCAL_SIMULADO);
        assertThat(pagina.itens()).isEmpty();
        assertThat(pagina.pagina()).isZero();
        assertThat(pagina.tamanho()).isEqualTo(50);
        assertThat(pagina.temProximaPagina()).isFalse();
    }

    @Test
    void repeticaoIdempotentePreservaRepresentacaoOriginalELocationERecusaConflito() throws Exception {
        Instant fim = fim();
        var original = consultas.solicitarAutorizacao(CNPJ, fim, "idempotencia");
        var corpo = Map.of("cnpjSacador", CNPJ, "fimVigencia", fim);
        var primeiraRepeticao = signataria.enviar(TOKEN_FINANCIADOR, "POST", BASE, corpo, "idempotencia");
        assertThat(primeiraRepeticao.statusCode()).isEqualTo(201);
        assertThat(primeiraRepeticao.headers().firstValue("Location").orElseThrow()).isEqualTo(caminho(original));
        assertThat(signataria.json.readValue(primeiraRepeticao.body(), AutorizacaoConsulta.class)).isEqualTo(original);
        decidir(original, TOKEN_SACADOR, "APROVAR");
        assertThat(consultas.solicitarAutorizacao(CNPJ, fim, "idempotencia")).isEqualTo(original);
        var repetida = signataria.enviar(TOKEN_FINANCIADOR, "POST", BASE, corpo, "idempotencia");
        assertThat(repetida.statusCode()).isEqualTo(201);
        assertThat(repetida.body()).isEqualTo(primeiraRepeticao.body());
        assertThat(repetida.headers().firstValue("Location")).isEqualTo(primeiraRepeticao.headers().firstValue("Location"));
        falha(() -> consultas.solicitarAutorizacao(CNPJ, fim.plusSeconds(1), "idempotencia"), Motivo.CONFLITO);
        assertThat(signataria.enviar(TOKEN_FINANCIADOR, "POST", BASE,
                Map.of("cnpjSacador", OUTRO_CNPJ, "fimVigencia", fim), "idempotencia").statusCode()).isEqualTo(409);
        assertThat(consultas.consultarAutorizacao(original.idAutorizacao()).status()).isEqualTo(AutorizacaoConsulta.Estado.ATIVA);
    }

    @Test
    void recusaDoSacadorImpedeConsulta() throws Exception {
        var autorizacao = solicitar(CNPJ, "recusada");
        decidir(autorizacao, TOKEN_SACADOR, "RECUSAR");
        assertThat(consultas.consultarAutorizacao(autorizacao.idAutorizacao()).status()).isEqualTo(AutorizacaoConsulta.Estado.RECUSADA);
        falha(() -> consultas.consultarDuplicatas(autorizacao.idAutorizacao()), Motivo.ACESSO_NEGADO);
    }

    @ParameterizedTest
    @CsvSource({"-1,50", "0,0", "0,101"})
    void rejeitaPaginacaoInvalidaLocalERemotamente(int pagina, int tamanho) throws Exception {
        var autorizacao = solicitar(CNPJ, "paginacao-" + pagina + "-" + tamanho);
        decidir(autorizacao, TOKEN_SACADOR, "APROVAR");
        assertThatThrownBy(() -> consultas.consultarDuplicatas(autorizacao.idAutorizacao(), pagina, tamanho))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(signataria.enviar(TOKEN_FINANCIADOR, "GET", caminho(autorizacao)
                + "/duplicatas?pagina=" + pagina + "&tamanho=" + tamanho, null, null).statusCode()).isEqualTo(400);
    }

    private static ConfigurableApplicationContext banco(String token) {
        return new SpringApplicationBuilder(FinanciamentoApplication.class).web(WebApplicationType.NONE).profiles("local")
                .initializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("vertical",
                        Map.of("SIGNATARIA_URL_BASE", signataria.url, "SIGNATARIA_TOKEN", token,
                                "SIGNATARIA_TIMEOUT_CONEXAO", "2s", "SIGNATARIA_TIMEOUT_LEITURA", "5s"))))
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR").run();
    }

    private static AutorizacaoConsulta solicitar(String cnpj, String chave) {
        return consultas.solicitarAutorizacao(cnpj, fim(), chave);
    }

    private static Instant fim() {
        return Instant.now().plusSeconds(600).truncatedTo(ChronoUnit.MICROS);
    }

    private static String caminho(AutorizacaoConsulta autorizacao) {
        return BASE + "/" + autorizacao.idAutorizacao();
    }

    private static void decidir(AutorizacaoConsulta autorizacao, String token, String decisao) throws Exception {
        assertThat(signataria.enviar(token, "PUT", caminho(autorizacao) + "/decisao",
                Map.of("decisao", decisao), null).statusCode()).isEqualTo(200);
    }

    private static void falha(Runnable operacao, Motivo motivo) {
        assertThatThrownBy(operacao::run).isInstanceOfSatisfying(FalhaRegistradora.class,
                falha -> assertThat(falha.motivo()).isEqualTo(motivo));
    }

    private static DuplicataConsultada escriturar(String token, String numero, String valor) throws Exception {
        var entrada = signataria.json.readTree("""
                {"sacado":{"cnpj":"12ABC34501DE35","nome":"Sacado fictício",
                  "domicilio":{"logradouro":"Rua Teste","numero":"1","bairro":"Centro","cep":"01001000",
                    "municipioIbge":"3550308","uf":"SP"}},
                 "tipoDuplicata":"MERCANTIL","numeroDuplicata":"%s","numeroFatura":"FAT-%s","numeroParcela":1,
                 "dataFaturamento":"2024-01-10","dataVencimento":"2030-02-10",
                 "valorFatura":%s,"valorDuplicata":%s,"documentoFiscal":{"tipo":"NFE","numero":"123","serie":"1"}}
                """.formatted(numero, numero, valor, valor));
        var recebido = signataria.enviar(token, "POST", "/api/v1/escrituracoes/manuais", entrada, "emissao-" + numero);
        assertThat(recebido.statusCode()).isEqualTo(202);
        assertThat(signataria.json.readTree(recebido.body()).get("status").asString()).isEqualTo("PENDENTE");
        String location = recebido.headers().firstValue("Location").orElseThrow();
        await().alias("Escrituração " + numero + " concluída pelo agendador").atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
                    var resposta = signataria.enviar(token, "GET", location, null, null);
                    assertThat(resposta.statusCode()).isEqualTo(200);
                    assertThat(signataria.json.readTree(resposta.body()).get("status").asString()).isEqualTo("CONCLUIDO");
                });
        var persistida = signataria.json.readTree(signataria.enviar(token, "GET", location, null, null).body());
        return new DuplicataConsultada(UUID.fromString(persistida.get("idDuplicata").asString()), numero, CNPJ,
                LocalDate.parse("2030-02-10"), new BigDecimal(valor), Instant.parse(persistida.get("escrituradaEm").asString()));
    }
}
