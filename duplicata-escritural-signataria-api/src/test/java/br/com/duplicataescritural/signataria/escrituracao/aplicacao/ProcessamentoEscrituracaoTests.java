package br.com.duplicataescritural.signataria.escrituracao.aplicacao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.CodigoResultado;
import br.com.duplicataescritural.signataria.escrituracao.dominio.DadosEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ExemploEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ReservaExecucao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Sacador;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.DuplicataRepository;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.ProcessamentoEscrituracaoRepository;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.SacadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao.Estado.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = "signataria.escrituracao.processamento.habilitado=false")
@Import(ProcessamentoEscrituracaoTests.RelogioTeste.class)
@ExtendWith(OutputCaptureExtension.class)
class ProcessamentoEscrituracaoTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired ServicoProcessamentoEscrituracao worker;
    @Autowired ServicoEscrituracao recebimento;
    @Autowired ProcessamentoEscrituracaoRepository processamentos;
    @Autowired DuplicataRepository duplicatas;
    @Autowired SacadorRepository sacadores;
    @Autowired JdbcClient jdbc;
    @Autowired RelogioControlavel relogio;
    @Autowired ConfiguracaoProcessamento configuracao;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationContext contexto;
    @Autowired io.micrometer.core.instrument.MeterRegistry metricas;
    @Autowired br.com.duplicataescritural.signataria.observabilidade.Observabilidade observabilidade;
    private Sacador sacador;

    @BeforeEach
    void preparar() {
        jdbc.sql("DROP TRIGGER IF EXISTS falhar_conclusao ON escrituradora.processamento_escrituracao").update();
        jdbc.sql("DROP FUNCTION IF EXISTS escrituradora.falhar_conclusao()").update();
        jdbc.sql("DROP SEQUENCE IF EXISTS escrituradora.falha_teste").update();
        processamentos.deleteAllInBatch();
        duplicatas.deleteAllInBatch();
        sacadores.deleteAllInBatch();
        sacador = sacadores.saveAndFlush(ExemploEscrituracao.sacador());
        relogio.instante.set(Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void metricasELogsRepresentamSomenteTransicoesConfirmadasAposRollbackENovaTentativa(CapturedOutput output) {
        double concluidas = transicoes("CONCLUIDO");
        double iniciadas = transicoes("EM_PROCESSAMENTO");
        double pendentes = transicoes("PENDENTE");
        var pedido = receber("observabilidade-rollback", ExemploEscrituracao.dados());
        falharConclusoes(1, "40001");
        worker.processarProximo();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(PENDENTE);
        assertThat(transicoes("CONCLUIDO")).isEqualTo(concluidas);
        assertThat(transicoes("EM_PROCESSAMENTO")).isEqualTo(iniciadas + 1);
        assertThat(transicoes("PENDENTE")).isEqualTo(pendentes + 1);
        relogio.avancar(Duration.ofSeconds(5));
        worker.processarProximo();
        assertThat(transicoes("CONCLUIDO")).isEqualTo(concluidas + 1);
        assertThat(transicoes("EM_PROCESSAMENTO")).isEqualTo(iniciadas + 2);
        assertThat(metricas.find("signataria.escrituracao.transicoes").counters()).allSatisfy(c ->
                assertThat(c.getId().getTags()).extracting(io.micrometer.core.instrument.Tag::getKey)
                        .containsExactlyInAnyOrder("estado", "motivo"));
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var eventos = output.getOut().lines().filter(l -> l.startsWith("{")).map(json::readTree)
                .filter(e -> pedido.id().toString().equals(e.path("processamentoId").asString())).toList();
        assertThat(eventos.stream().filter(e -> "CONCLUIDO".equals(e.path("estado").asString())).count()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("erro transitorio no commit", "valor-sensivel");
    }

    private double transicoes(String estado) {
        return metricas.find("signataria.escrituracao.transicoes").tag("estado", estado).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }

    @Test
    void criaUmaDuplicataEConcluiAtomicamentePreservandoPedidoEIdempotencia() {
        var pedido = receber("valido", ExemploEscrituracao.dados());
        var reserva = worker.reservarProximo().orElseThrow();
        var reservado = buscar(pedido.id());
        assertThat(reservado.estado()).isEqualTo(EM_PROCESSAMENTO);
        assertThat(reservado.tentativas()).isEqualTo(1);
        assertThat(reservado.reservaExpiraEm()).isEqualTo(relogio.instant().plusSeconds(60));
        worker.executar(reserva);
        worker.executar(reserva);
        worker.processarProximo();

        var resultado = buscar(pedido.id());
        assertThat(resultado.estado()).isEqualTo(CONCLUIDO);
        assertThat(resultado.finalizadoEm()).isEqualTo(relogio.instant());
        assertThat(resultado.escrituradaEm()).isEqualTo(relogio.instant());
        assertThat(resultado.reservaExpiraEm()).isNull();
        assertThat(resultado.codigoResultado()).isNull();
        assertThat(resultado.dados()).isEqualTo(pedido.dados());
        assertThat(resultado.sacadorNoRecebimento()).isEqualTo(pedido.sacadorNoRecebimento());
        assertThat(resultado.chaveIdempotencia()).isEqualTo("valido");
        var titulo = duplicatas.findById(resultado.idDuplicata()).orElseThrow();
        assertThat(titulo.sacadorId()).isEqualTo(sacador.id());
        assertThat(titulo.sacadorCnpj()).isEqualTo(sacador.cnpj());
        assertThat(titulo.dados()).isEqualTo(pedido.dados());
        assertThat(titulo.escrituradaEm()).isEqualTo(resultado.escrituradaEm());
        assertThat(duplicatas.count()).isEqualTo(1);
        assertThat(receber("valido", pedido.dados()).id()).isEqualTo(pedido.id());
        assertThat(processamentos.count()).isEqualTo(1);
        assertThat(contexto.getBeansOfType(AgendadorEscrituracao.class)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"DUP-1,FAT-OUTRA", "DUP-OUTRA,FAT-1"})
    void rejeitaCadaUnicidadeComercialDepoisDoRollback(String numero, String fatura, CapturedOutput output) {
        double rejeitadas = transicoes("REJEITADO");
        receber("primeiro", ExemploEscrituracao.dados());
        worker.processarProximo();
        var repetido = receber("segundo", dados(numero, fatura));
        worker.processarProximo();

        var resultado = buscar(repetido.id());
        assertThat(resultado.estado()).isEqualTo(REJEITADO);
        assertThat(transicoes("REJEITADO")).isEqualTo(rejeitadas + 1);
        assertThat(resultado.codigoResultado()).isEqualTo(CodigoResultado.DUPLICATA_JA_ESCRITURADA);
        assertThat(resultado.finalizadoEm()).isEqualTo(relogio.instant());
        assertThat(resultado.tentativas()).isEqualTo(1);
        assertThat(resultado.idDuplicata()).isNull();
        relogio.avancar(Duration.ofDays(1));
        worker.processarProximo();
        assertThat(buscar(repetido.id()).tentativas()).isEqualTo(1);
        assertThat(duplicatas.count()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("Key (sacador_id", "ERROR: duplicate key");
        assertThat(output.getAll()).contains("ErrorCode: 0, SQLState: 23505");
    }

    @Test
    void documentoFiscalIsoladoNaoImpedeTitulosDiferentes() {
        receber("um", ExemploEscrituracao.dados());
        receber("dois", dados("DUP-2", "FAT-2"));
        worker.processarProximo();
        worker.processarProximo();
        assertThat(duplicatas.count()).isEqualTo(2);
        assertThat(processamentos.findAll()).allSatisfy(p -> assertThat(p.estado()).isEqualTo(CONCLUIDO));
    }

    @Test
    void duasSolicitacoesConcorrentesDoMesmoTituloTemSomenteUmEfeito() throws Exception {
        var recebidos = new CyclicBarrier(2);
        Callable<UUID> primeiro = () -> {
            var pedido = receber("concorrente-1", ExemploEscrituracao.dados());
            recebidos.await(10, TimeUnit.SECONDS);
            worker.processarProximo();
            return pedido.id();
        };
        Callable<UUID> segundo = () -> {
            var pedido = receber("concorrente-2", ExemploEscrituracao.dados());
            recebidos.await(10, TimeUnit.SECONDS);
            worker.processarProximo();
            return pedido.id();
        };
        var ids = concorrer(primeiro, segundo);
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids.stream().map(this::buscar).map(ProcessamentoEscrituracao::estado))
                .containsExactlyInAnyOrder(CONCLUIDO, REJEITADO);
        assertThat(duplicatas.count()).isEqualTo(1);
    }

    @Test
    void doisConsumidoresDisputamUmTrabalhoSemDuplicarReserva() throws Exception {
        var pedido = receber("disputa", ExemploEscrituracao.dados());
        var largada = new CyclicBarrier(2);
        Callable<java.util.Optional<ReservaExecucao>> consumidor = () -> {
            largada.await(10, TimeUnit.SECONDS);
            return worker.reservarProximo();
        };
        var reservas = concorrer(consumidor, consumidor);
        assertThat(reservas.stream().flatMap(java.util.Optional::stream)).hasSize(1);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(1);
    }

    @Test
    void skipLockedIgnoraTrabalhoBloqueadoSemEsperar() throws Exception {
        var primeiro = receber("bloqueado", ExemploEscrituracao.dados());
        var segundo = receber("livre", dados("DUP-2", "FAT-2"));
        var bloqueado = new java.util.concurrent.CountDownLatch(1);
        var liberar = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var lock = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                processamentos.bloquear(primeiro.id()).orElseThrow();
                bloqueado.countDown();
                try {
                    if (!liberar.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Lock não liberado");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            try {
                assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
                var reserva = executor.submit(worker::reservarProximo).get(5, TimeUnit.SECONDS).orElseThrow();
                assertThat(reserva.idProcessamento()).isEqualTo(segundo.id());
            } finally {
                liberar.countDown();
            }
            lock.get(10, TimeUnit.SECONDS);
        }
        assertThat(buscar(primeiro.id()).tentativas()).isZero();
    }

    @Test
    void falhaDepoisDoInsertDesfazDuplicataEPermiteSegundaTentativa(CapturedOutput output) {
        falharConclusoes(1, "40001");
        var pedido = receber("rollback", ExemploEscrituracao.dados());
        var reserva = worker.reservarProximo().orElseThrow();
        var erro = catchThrowableOfType(RuntimeException.class, () -> worker.executar(reserva));
        assertThat(erro).isNotNull();
        assertThat(duplicatas.count()).isZero();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(EM_PROCESSAMENTO);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(1);
        worker.registrarFalha(reserva, erro);
        var pendente = buscar(pedido.id());
        assertThat(pendente.estado()).isEqualTo(PENDENTE);
        assertThat(pendente.proximaExecucaoEm()).isEqualTo(relogio.instant().plusSeconds(5));
        assertThat(worker.reservarProximo()).isEmpty();
        relogio.avancar(Duration.ofSeconds(4));
        assertThat(worker.reservarProximo()).isEmpty();
        relogio.avancar(Duration.ofSeconds(1));
        worker.processarProximo();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(CONCLUIDO);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(2);
        assertThat(duplicatas.count()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("diagnostico-interno-teste");
        assertThat(output.getAll()).contains("ErrorCode: 0, SQLState: 40001");
    }

    @Test
    void falhasTecnicasEsgotamTresTentativasComBackoffDuravel() {
        falharConclusoes(3, "40001");
        var pedido = receber("esgotamento", ExemploEscrituracao.dados());
        worker.processarProximo();
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(1);
        relogio.avancar(Duration.ofSeconds(5));
        worker.processarProximo();
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(2);
        assertThat(buscar(pedido.id()).proximaExecucaoEm()).isEqualTo(relogio.instant().plusSeconds(30));
        relogio.avancar(Duration.ofSeconds(29));
        assertThat(worker.reservarProximo()).isEmpty();
        relogio.avancar(Duration.ofSeconds(1));
        worker.processarProximo();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(FALHA_TECNICA);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(3);
        assertThat(buscar(pedido.id()).finalizadoEm()).isEqualTo(relogio.instant());
        relogio.avancar(Duration.ofDays(1));
        worker.processarProximo();
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(3);
        assertThat(duplicatas.count()).isZero();
    }

    @Test
    void falhaNaoTransitoriaFinalizaSemRepetir(CapturedOutput output) {
        double falhas = transicoes("FALHA_TECNICA");
        falharConclusoes(100, "23514");
        var pedido = receber("nao-transitoria", ExemploEscrituracao.dados());
        worker.processarProximo();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(FALHA_TECNICA);
        assertThat(transicoes("FALHA_TECNICA")).isEqualTo(falhas + 1);
        assertThat(buscar(pedido.id()).codigoResultado()).isEqualTo(CodigoResultado.ERRO_TECNICO);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(1);
        assertThat(duplicatas.count()).isZero();
        assertThat(output.getAll()).contains("ErrorCode: 0, SQLState: 23514")
                .doesNotContain("diagnostico-interno-teste");
    }

    @Test
    void diagnosticoJdbcPreservaCodigosSemMensagensOuStackTrace(CapturedOutput output) {
        var logger = org.slf4j.LoggerFactory.getLogger("org.hibernate.orm.jdbc.error");
        assertThat(logger.isWarnEnabled()).isTrue();
        logger.warn("HHH000247: ErrorCode: 0, SQLState: 08006");
        logger.error("cnpj-sensivel\nvalor-sensivel", new IllegalStateException("causa-sensivel"));
        logger.warn("HHH000247: ErrorCode: 0, SQLState: 23505\nconteudo-sensivel");
        assertThat(output.getAll()).contains("ErrorCode: 0, SQLState: 08006", "Detalhes JDBC omitidos por segurança")
                .doesNotContain("cnpj-sensivel", "valor-sensivel", "causa-sensivel", "conteudo-sensivel",
                        "java.lang.IllegalStateException");
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var eventos = output.getOut().lines().filter(l -> l.startsWith("{")).map(json::readTree)
                .filter(e -> "org.hibernate.orm.jdbc.error".equals(e.path("logger_name").asString())).toList();
        assertThat(eventos).isNotEmpty().allSatisfy(e -> {
            assertThat(e.path("service").asString()).isEqualTo("duplicata-escritural-signataria-api");
            assertThat(e.has("@timestamp") && e.has("level") && e.has("message")).isTrue();
        });
        assertThat(eventos).extracting(e -> e.path("message").asString())
                .contains("HHH000247: ErrorCode: 0, SQLState: 08006", "Detalhes JDBC omitidos por segurança");
    }

    @ParameterizedTest
    @CsvSource({"08006,PENDENTE", "40001,PENDENTE", "40P01,PENDENTE", "23514,FALHA_TECNICA", "28000,FALHA_TECNICA"})
    void classificaSqlStateEncapsuladoSemRetryDeErroPermanente(String sqlState, String destino) {
        var pedido = receber("sqlstate", ExemploEscrituracao.dados());
        var reserva = worker.reservarProximo().orElseThrow();
        worker.registrarFalha(reserva, new org.springframework.transaction.TransactionSystemException(
                "commit incerto", new java.sql.SQLException("mensagem interna", sqlState)));
        assertThat(buscar(pedido.id()).estado().name()).isEqualTo(destino);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(1);
    }

    @Test
    void timeoutTransacionalLimitaEsperaPorLockNoPostgresql() throws Exception {
        var pedido = receber("timeout", ExemploEscrituracao.dados());
        var reserva = worker.reservarProximo().orElseThrow();
        var curta = new ConfiguracaoProcessamento(false, Duration.ofSeconds(1), 3, Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(1));
        var executorCurto = new ServicoProcessamentoEscrituracao(processamentos, duplicatas, sacadores,
                relogio, curta, transactionManager, observabilidade);
        var bloqueado = new java.util.concurrent.CountDownLatch(1);
        var liberar = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var lock = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                processamentos.bloquear(pedido.id()).orElseThrow();
                bloqueado.countDown();
                try {
                    if (!liberar.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Lock não liberado");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            try {
                assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
                var erro = executor.submit(() -> catchThrowableOfType(RuntimeException.class,
                        () -> executorCurto.executar(reserva))).get(5, TimeUnit.SECONDS);
                assertThat(erro).isNotNull();
            } finally {
                liberar.countDown();
            }
            lock.get(10, TimeUnit.SECONDS);
        }
        assertThat(buscar(pedido.id()).estado()).isEqualTo(EM_PROCESSAMENTO);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(1);
        assertThat(duplicatas.count()).isZero();
    }

    @Test
    void falhaEmT3MantemReservaDuravelParaRecuperacao() {
        var pedido = receber("falha-t3", ExemploEscrituracao.dados());
        var reserva = worker.reservarProximo().orElseThrow();
        falharConclusoes(1, "40001");
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION escrituradora.falhar_conclusao() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.estado = 'PENDENTE' AND nextval('escrituradora.falha_teste') = 1 THEN
                        RAISE EXCEPTION 'falha tecnica em T3' USING ERRCODE = '40001';
                    END IF;
                    RETURN NEW;
                END $$
                """).update();
        assertThatThrownBy(() -> worker.registrarFalha(reserva,
                new TransientDataAccessResourceException("falha técnica em T2"))).isInstanceOf(RuntimeException.class);
        assertThat(buscar(pedido.id()).estado()).isEqualTo(EM_PROCESSAMENTO);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(1);
        relogio.avancar(Duration.ofSeconds(60));
        worker.reservarProximo();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(PENDENTE);
        relogio.avancar(Duration.ofSeconds(5));
        worker.processarProximo();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(CONCLUIDO);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(2);
    }

    @Test
    void recuperaReservaAbandonadaComNovoCoordenadorSemPerderNumero() {
        var pedido = receber("reinicio", ExemploEscrituracao.dados());
        var original = worker.reservarProximo().orElseThrow();
        var reiniciado = new ServicoProcessamentoEscrituracao(processamentos, duplicatas, sacadores,
                relogio, configuracao, transactionManager, observabilidade);
        relogio.avancar(Duration.ofSeconds(59));
        assertThat(reiniciado.reservarProximo()).isEmpty();
        relogio.avancar(Duration.ofSeconds(1));
        assertThat(reiniciado.reservarProximo()).isEmpty();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(PENDENTE);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(original.tentativa());
        assertThat(buscar(pedido.id()).codigoResultado()).isEqualTo(CodigoResultado.RESERVA_EXPIRADA);
        relogio.avancar(Duration.ofSeconds(5));
        reiniciado.processarProximo();
        assertThat(buscar(pedido.id()).estado()).isEqualTo(CONCLUIDO);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(2);
    }

    @Test
    void terceiraReservaAbandonadaFinalizaSemQuartaExecucao() {
        var pedido = receber("abandonos", ExemploEscrituracao.dados());
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            assertThat(worker.reservarProximo().orElseThrow().tentativa()).isEqualTo(tentativa);
            relogio.avancar(Duration.ofSeconds(60));
            assertThat(worker.reservarProximo()).isEmpty();
            relogio.avancar(configuracao.esperaApos(tentativa));
        }
        assertThat(buscar(pedido.id()).estado()).isEqualTo(FALHA_TECNICA);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(3);
        assertThat(worker.reservarProximo()).isEmpty();
        assertThat(duplicatas.count()).isZero();
    }

    @Test
    void execucaoEFalhaAntigasNaoSobrescrevemNovaTentativaNemResultado() {
        var pedido = receber("atrasada", ExemploEscrituracao.dados());
        var antiga = worker.reservarProximo().orElseThrow();
        relogio.avancar(Duration.ofSeconds(60));
        worker.executar(antiga);
        assertThat(duplicatas.count()).isZero();
        worker.reservarProximo();
        relogio.avancar(Duration.ofSeconds(5));
        var atual = worker.reservarProximo().orElseThrow();
        worker.executar(antiga);
        worker.registrarFalha(antiga, new TransientDataAccessResourceException("erro antigo"));
        assertThat(buscar(pedido.id()).estado()).isEqualTo(EM_PROCESSAMENTO);
        assertThat(buscar(pedido.id()).tentativas()).isEqualTo(2);
        worker.executar(atual);
        var id = buscar(pedido.id()).idDuplicata();
        worker.executar(antiga);
        worker.registrarFalha(atual, new TransientDataAccessResourceException("falha após commit confirmado"));
        assertThat(buscar(pedido.id()).estado()).isEqualTo(CONCLUIDO);
        assertThat(buscar(pedido.id()).idDuplicata()).isEqualTo(id);
        assertThat(duplicatas.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"1,NEGOCIO", "1,TRANSITORIA", "1,PERMANENTE", "3,NEGOCIO", "3,TRANSITORIA", "3,PERMANENTE"})
    void resultadoAtrasadoNaoAlteraReservaExpiradaSemNovaTentativa(int tentativa, String tipoFalha) {
        var pedido = receber("expirada-sem-sucessora", ExemploEscrituracao.dados());
        var reserva = worker.reservarProximo().orElseThrow();
        for (int anterior = 1; anterior < tentativa; anterior++) {
            worker.registrarFalha(reserva, new TransientDataAccessResourceException("falha anterior"));
            relogio.avancar(configuracao.esperaApos(anterior));
            reserva = worker.reservarProximo().orElseThrow();
        }
        var antes = buscar(pedido.id());
        relogio.avancar(configuracao.duracaoReserva());
        RuntimeException falha = switch (tipoFalha) {
            case "NEGOCIO" -> new br.com.duplicataescritural.signataria.escrituracao.dominio.RejeicaoEscrituracao(
                    CodigoResultado.DUPLICATA_JA_ESCRITURADA);
            case "TRANSITORIA" -> new TransientDataAccessResourceException("resposta atrasada");
            default -> new IllegalStateException("resposta atrasada");
        };
        worker.registrarFalha(reserva, falha);
        var depois = buscar(pedido.id());
        assertThat(depois.estado()).isEqualTo(EM_PROCESSAMENTO);
        assertThat(depois.tentativas()).isEqualTo(tentativa);
        assertThat(depois.reservaExpiraEm()).isEqualTo(antes.reservaExpiraEm());
        assertThat(depois.proximaExecucaoEm()).isEqualTo(antes.proximaExecucaoEm());
        assertThat(depois.codigoResultado()).isEqualTo(antes.codigoResultado());
        assertThat(depois.finalizadoEm()).isNull();
        assertThat(duplicatas.count()).isZero();

        var expirada = reserva;
        assertThatIllegalStateException().isThrownBy(() -> depois.rejeitar(expirada,
                CodigoResultado.DUPLICATA_JA_ESCRITURADA, relogio.instant()));
        assertThatIllegalStateException().isThrownBy(() -> depois.falhar(expirada, relogio.instant(), true,
                configuracao.maximoTentativas(), configuracao.esperaApos(tentativa), CodigoResultado.FALHA_TRANSITORIA));
        assertThatIllegalStateException().isThrownBy(() -> depois.falhar(expirada, relogio.instant(), false,
                configuracao.maximoTentativas(), configuracao.esperaApos(tentativa), CodigoResultado.ERRO_TECNICO));

        assertThat(worker.reservarProximo()).isEmpty();
        var recuperado = buscar(pedido.id());
        assertThat(recuperado.estado()).isEqualTo(tentativa == 3 ? FALHA_TECNICA : PENDENTE);
        assertThat(recuperado.codigoResultado()).isEqualTo(CodigoResultado.RESERVA_EXPIRADA);
        assertThat(recuperado.tentativas()).isEqualTo(tentativa);
    }

    @Test
    void sacadorDesabilitadoDepoisDoRecebimentoEhRejeitadoSemRetry() {
        var pedido = receber("habilitacao", ExemploEscrituracao.dados());
        jdbc.sql("UPDATE escrituradora.sacador SET habilitado = false WHERE id = :id").param("id", sacador.id()).update();
        worker.processarProximo();
        var resultado = buscar(pedido.id());
        assertThat(resultado.estado()).isEqualTo(REJEITADO);
        assertThat(resultado.codigoResultado()).isEqualTo(CodigoResultado.SACADOR_NAO_HABILITADO);
        assertThat(resultado.sacadorNoRecebimento().habilitado()).isTrue();
        assertThat(worker.reservarProximo()).isEmpty();
        assertThat(duplicatas.count()).isZero();
    }

    @Test
    void transicoesDoDominioProtegemReservaETerminais() {
        var pedido = receber("invariantes", ExemploEscrituracao.dados());
        var reserva = worker.reservarProximo().orElseThrow();
        var processamento = buscar(pedido.id());
        var duplicata = br.com.duplicataescritural.signataria.escrituracao.dominio.Duplicata
                .escriturar(processamento, relogio.instant());
        assertThatIllegalStateException().isThrownBy(() -> processamento.reservar(relogio.instant(), Duration.ofSeconds(60), 3));
        assertThatIllegalStateException().isThrownBy(() -> processamento.concluir(
                new ReservaExecucao(pedido.id(), 2), duplicata, relogio.instant()));
        assertThatIllegalArgumentException().isThrownBy(() -> processamento.rejeitar(reserva,
                CodigoResultado.ERRO_TECNICO, relogio.instant()));
        assertThatIllegalStateException().isThrownBy(() -> processamento.falhar(reserva, relogio.instant(), true,
                configuracao.maximoTentativas(), configuracao.esperaApos(1), CodigoResultado.RESERVA_EXPIRADA));
        assertThatIllegalStateException().isThrownBy(() -> processamento.concluir(reserva, duplicata,
                processamento.reservaExpiraEm()));
        processamento.concluir(reserva, duplicata, relogio.instant());
        assertThatIllegalStateException().isThrownBy(() -> processamento.rejeitar(reserva,
                CodigoResultado.DUPLICATA_JA_ESCRITURADA, relogio.instant()));
    }

    private ProcessamentoEscrituracao receber(String chave, DadosEscrituracao dados) {
        return recebimento.receber(sacador.fotografia(), new ChaveIdempotencia(chave), dados);
    }

    private ProcessamentoEscrituracao buscar(UUID id) {
        return processamentos.findById(id).orElseThrow();
    }

    private DadosEscrituracao dados(String numero, String fatura) {
        var base = ExemploEscrituracao.dados();
        return new DadosEscrituracao(base.sacado(), base.tipoDuplicata(), numero, fatura, base.numeroParcela(),
                base.dataFaturamento(), base.dataVencimento(), base.valorFatura(), base.valorDuplicata(), base.documentoFiscal());
    }

    private <T> List<T> concorrer(Callable<T> primeiro, Callable<T> segundo) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(primeiro);
            var b = executor.submit(segundo);
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    private void falharConclusoes(int quantidade, String sqlState) {
        // nextval não sofre rollback: permite falhar uma vez exatamente após o INSERT real da Duplicata.
        jdbc.sql("CREATE SEQUENCE escrituradora.falha_teste").update();
        jdbc.sql("""
                CREATE FUNCTION escrituradora.falhar_conclusao() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.estado = 'CONCLUIDO' AND nextval('escrituradora.falha_teste') <= %d THEN
                        RAISE EXCEPTION 'diagnostico-interno-teste' USING ERRCODE = '%s';
                    END IF;
                    RETURN NEW;
                END $$
                """.formatted(quantidade, sqlState)).update();
        jdbc.sql("""
                CREATE TRIGGER falhar_conclusao BEFORE UPDATE ON escrituradora.processamento_escrituracao
                FOR EACH ROW EXECUTE FUNCTION escrituradora.falhar_conclusao()
                """).update();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RelogioTeste {
        @Bean
        @Primary
        RelogioControlavel relogioControlavel() { return new RelogioControlavel(); }
    }

    static final class RelogioControlavel extends Clock {
        final AtomicReference<Instant> instante = new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        void avancar(Duration duracao) { instante.updateAndGet(agora -> agora.plus(duracao)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instante.get(); }
    }
}
