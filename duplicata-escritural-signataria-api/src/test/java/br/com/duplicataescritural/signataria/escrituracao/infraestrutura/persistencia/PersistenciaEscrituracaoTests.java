package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.aplicacao.ServicoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.aplicacao.ServicoProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ExemploEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Sacador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@Testcontainers
@SpringBootTest(properties = "signataria.escrituracao.processamento.habilitado=false")
class PersistenciaEscrituracaoTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired SacadorRepository sacadores;
    @Autowired ProcessamentoEscrituracaoRepository processamentos;
    @Autowired ServicoEscrituracao servico;
    @Autowired ServicoProcessamentoEscrituracao worker;
    @Autowired DuplicataRepository duplicatas;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void limparBancoDescartavel() {
        processamentos.deleteAllInBatch();
        duplicatas.deleteAllInBatch();
        sacadores.deleteAllInBatch();
    }

    @Test
    void salvaEConsultaSacadorComCnpjAlfanumericoPeloJpa() {
        var sacador = new Sacador(UUID.randomUUID(), new Cnpj("12.abc.345/01de-35"), "Sacador JPA",
                ExemploEscrituracao.endereco(), LocalDate.of(2020, 1, 1), true);
        sacadores.saveAndFlush(sacador);

        var recuperado = sacadores.findById(sacador.id()).orElseThrow();
        assertThat(recuperado.fotografia()).isEqualTo(sacador.fotografia());
        assertThat(jdbc.sql("SELECT cnpj FROM escrituradora.sacador WHERE id = :id").param("id", sacador.id())
                .query(String.class).single()).isEqualTo("12ABC34501DE35");
    }

    @Test
    void consultaPorJpaJsonGravadoNoFormatoAnteriorSemMigracao() {
        var sacador = sacadores.saveAndFlush(ExemploEscrituracao.sacador());
        UUID id = UUID.randomUUID();
        String dadosAnteriores = """
                {"sacado":{"cnpj":"12ABC34501DE35","nome":"Sacado de Teste","domicilio":{
                "logradouro":"Rua de Teste","numero":"10","bairro":"Centro","cep":"01001000",
                "municipioIbge":"3550308","uf":"SP"}},"tipoDuplicata":"MERCANTIL","numeroDuplicata":"DUP-1",
                "numeroFatura":"FAT-1","numeroParcela":1,"dataFaturamento":"2024-01-10",
                "dataVencimento":"2024-02-10","valorFatura":100.00,"valorDuplicata":100.00,
                "documentoFiscal":{"tipo":"NFE","numero":"123","serie":"1"}}
                """;
        String fotografiaAnterior = """
                {"id":"%s","cnpj":{"valor":"11222333000181"},"nome":"Sacador de Teste","domicilio":{
                "logradouro":"Rua de Teste","numero":"10","bairro":"Centro","cep":"01001000",
                "municipioIbge":"3550308","uf":"SP"},"inicioOperacao":"2020-01-01","habilitado":true}
                """.formatted(sacador.id());
        jdbc.sql("""
                INSERT INTO escrituradora.processamento_escrituracao
                    (id, sacador_id, operacao, chave_idempotencia, dados, sacador_no_recebimento, recebido_em, estado)
                VALUES (:id, :sacador, 'ESCRITURACAO_MANUAL', 'formato-anterior', CAST(:dados AS jsonb),
                    CAST(:fotografia AS jsonb), TIMESTAMPTZ '2026-11-12 15:00:00+00', 'PENDENTE')
                """).param("id", id).param("sacador", sacador.id()).param("dados", dadosAnteriores)
                .param("fotografia", fotografiaAnterior).update();

        var recuperado = processamentos.findByIdAndSacadorId(id, sacador.id()).orElseThrow();
        assertThat(recuperado.dados()).isEqualTo(ExemploEscrituracao.dados());
        assertThat(recuperado.sacadorNoRecebimento()).isEqualTo(sacador.fotografia());
        assertThat(recuperado.recebidoEm()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z"));
        assertThat(recuperado.estado()).isEqualTo(ProcessamentoEscrituracao.Estado.PENDENTE);
        worker.processarProximo();
        var concluido = processamentos.findById(id).orElseThrow();
        assertThat(concluido.estado()).isEqualTo(ProcessamentoEscrituracao.Estado.CONCLUIDO);
        assertThat(concluido.tentativas()).isEqualTo(1);
        assertThat(concluido.dados()).isEqualTo(recuperado.dados());
        assertThat(concluido.sacadorNoRecebimento()).isEqualTo(recuperado.sacadorNoRecebimento());
        assertThat(duplicatas.findById(concluido.idDuplicata()).orElseThrow().dados()).isEqualTo(recuperado.dados());
    }

    @Test
    void rollbackDesfazJpaEInsercaoJdbcNaMesmaTransacao() {
        var sacador = sacadores.saveAndFlush(ExemploEscrituracao.sacador());
        var transacao = new TransactionTemplate(transactionManager);
        transacao.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        UUID outroSacador = UUID.randomUUID();

        assertThatIllegalStateException().isThrownBy(() -> transacao.executeWithoutResult(status -> {
            sacadores.saveAndFlush(new Sacador(outroSacador, new Cnpj("12ABC34501DE35"), "Outro Sacador",
                    ExemploEscrituracao.endereco(), LocalDate.of(2020, 1, 1), true));
            var processamento = servico.receber(sacador.fotografia(), new ChaveIdempotencia("rollback"),
                    ExemploEscrituracao.dados());
            assertThat(processamentos.findByIdAndSacadorId(processamento.id(), sacador.id())).isPresent();
            throw new IllegalStateException("Falha simulada antes do commit");
        }));

        assertThat(sacadores.findById(outroSacador)).isEmpty();
        assertThat(sacadores.count()).isEqualTo(1);
        assertThat(processamentos.count()).isZero();
    }
}
