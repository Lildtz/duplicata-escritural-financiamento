package br.com.duplicataescritural.signataria.consultarecebiveis.infraestrutura;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import br.com.duplicataescritural.signataria.SignatariaApplication;
import br.com.duplicataescritural.signataria.consultarecebiveis.aplicacao.ServicoAutorizacaoConsulta;
import br.com.duplicataescritural.signataria.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ExemploEscrituracao;
import br.com.duplicataescritural.signataria.seguranca.FinanciadorAutenticado;
import br.com.duplicataescritural.signataria.seguranca.SacadorAutenticado;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ReinicioAutorizacaoTests {
    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18-alpine");

    @Test
    void aplicaV3SemAlterarV1V2EDadosEConservaAutorizacoesAposReinicio() {
        var v2 = Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas("escrituradora").target("2").load();
        v2.migrate();
        var checksums = Arrays.stream(v2.info().applied()).filter(m -> m.getVersion() != null).map(m -> m.getChecksum()).toList();
        var jdbc = JdbcClient.create(new DriverManagerDataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword()));
        var json = new JsonMapper();
        var sacador = ExemploEscrituracao.sacador();
        jdbc.sql("""
                INSERT INTO escrituradora.sacador (id, cnpj, nome, domicilio, inicio_operacao, habilitado)
                VALUES (:id, :cnpj, :nome, CAST(:domicilio AS jsonb), :inicio, true)
                """).param("id", sacador.id()).param("cnpj", sacador.cnpj().valor()).param("nome", sacador.nome())
                .param("domicilio", json.writeValueAsString(sacador.domicilio())).param("inicio", sacador.inicioOperacao()).update();
        jdbc.sql("""
                INSERT INTO escrituradora.processamento_escrituracao
                    (id, sacador_id, operacao, chave_idempotencia, dados, sacador_no_recebimento, recebido_em, estado)
                VALUES (:id, :sacador, 'ESCRITURACAO_MANUAL', 'legado-v2', CAST(:dados AS jsonb),
                    CAST(:fotografia AS jsonb), CURRENT_TIMESTAMP, 'PENDENTE')
                """).param("id", UUID.randomUUID()).param("sacador", sacador.id())
                .param("dados", json.writeValueAsString(ExemploEscrituracao.dados()))
                .param("fotografia", json.writeValueAsString(sacador.fotografia())).update();
        jdbc.sql("""
                INSERT INTO escrituradora.duplicata
                    (id, sacador_id, sacador_cnpj, numero_duplicata, numero_fatura, numero_parcela, dados, escriturada_em)
                VALUES (:id, :sacador, :cnpj, 'DUP-1', 'FAT-1', 1, CAST(:dados AS jsonb), CURRENT_TIMESTAMP)
                """).param("id", UUID.randomUUID()).param("sacador", sacador.id()).param("cnpj", sacador.cnpj().valor())
                .param("dados", json.writeValueAsString(ExemploEscrituracao.dados())).update();
        String pedidoLegado = jdbc.sql("SELECT to_jsonb(p)::text FROM escrituradora.processamento_escrituracao p").query(String.class).single();
        String tituloLegado = jdbc.sql("SELECT to_jsonb(d)::text FROM escrituradora.duplicata d").query(String.class).single();
        var financiador = new FinanciadorAutenticado(UUID.randomUUID());
        var chave = new ChaveIdempotencia("persistencia");
        Instant fim = Instant.now().plusSeconds(3600);
        UUID id;
        Instant decidida;
        try (var primeira = iniciar(financiador.id())) {
            var flyway = primeira.getBean(Flyway.class);
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");
            assertThat(Arrays.stream(flyway.info().applied()).filter(m -> m.getVersion() != null)
                    .limit(2).map(m -> m.getChecksum()).toList()).isEqualTo(checksums);
            flyway.validate();
            assertThat(jdbc.sql("SELECT to_jsonb(p)::text FROM escrituradora.processamento_escrituracao p").query(String.class).single()).isEqualTo(pedidoLegado);
            assertThat(jdbc.sql("SELECT to_jsonb(d)::text FROM escrituradora.duplicata d").query(String.class).single()).isEqualTo(tituloLegado);
            var servico = primeira.getBean(ServicoAutorizacaoConsulta.class);
            var autorizacao = servico.solicitar(financiador, chave, sacador.cnpj(), fim);
            id = autorizacao.id();
            decidida = servico.decidir(new SacadorAutenticado(sacador.fotografia()), id, AutorizacaoConsulta.Decisao.APROVAR).decididaEm();
        }
        try (var segunda = iniciar(financiador.id())) {
            var servico = segunda.getBean(ServicoAutorizacaoConsulta.class);
            var persistida = servico.consultar(financiador, id);
            assertThat(persistida.estado()).isEqualTo(AutorizacaoConsulta.Estado.ATIVA);
            assertThat(persistida.decididaEm()).isEqualTo(decidida);
            assertThat(persistida.vigente(Instant.now())).isTrue();
            assertThat(servico.solicitar(financiador, chave, new Cnpj("11.222.333/0001-81"), fim).id()).isEqualTo(id);
            servico.revogar(financiador, id);
        }
        try (var terceira = iniciar(financiador.id())) {
            assertThat(terceira.getBean(ServicoAutorizacaoConsulta.class).consultar(financiador, id).estado())
                    .isEqualTo(AutorizacaoConsulta.Estado.REVOGADA);
        }
    }

    private ConfigurableApplicationContext iniciar(UUID financiador) {
        return new SpringApplicationBuilder(SignatariaApplication.class).run(
                "--server.port=0", "--spring.profiles.active=local",
                "--spring.datasource.url=" + POSTGRESQL.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRESQL.getUsername(),
                "--spring.datasource.password=" + POSTGRESQL.getPassword(),
                "--signataria.escrituracao.processamento.habilitado=false",
                "--signataria.autenticacao-local.tokens[0].sha256=" + "a".repeat(64),
                "--signataria.autenticacao-local.tokens[0].financiador-id=" + financiador);
    }
}
