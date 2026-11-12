package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.Arrays;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ExemploEscrituracao;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MigracaoV2Tests {
    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18-alpine");

    @Test
    void aplicaV2IncrementalmentePreservandoV1EPedidoExistente() throws Exception {
        var v1 = Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas("escrituradora").target("1").load();
        assertThat(v1.migrate().migrationsExecuted).isEqualTo(1);
        var checksum = v1.info().current().getChecksum();
        var sacador = ExemploEscrituracao.sacador();
        UUID id = UUID.randomUUID();
        var json = new JsonMapper();
        String dados = json.writeValueAsString(ExemploEscrituracao.dados());
        String fotografia = json.writeValueAsString(sacador.fotografia());
        try (var conexao = DriverManager.getConnection(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())) {
            try (var cadastro = conexao.prepareStatement("""
                    INSERT INTO escrituradora.sacador (id, cnpj, nome, domicilio, inicio_operacao, habilitado)
                    VALUES (?, ?, ?, CAST(? AS jsonb), ?, true)
                    """)) {
                cadastro.setObject(1, sacador.id());
                cadastro.setString(2, sacador.cnpj().valor());
                cadastro.setString(3, sacador.nome());
                cadastro.setString(4, json.writeValueAsString(sacador.domicilio()));
                cadastro.setObject(5, sacador.inicioOperacao());
                cadastro.executeUpdate();
            }
            try (var pedido = conexao.prepareStatement("""
                    INSERT INTO escrituradora.processamento_escrituracao
                        (id, sacador_id, operacao, chave_idempotencia, dados, sacador_no_recebimento, recebido_em, estado)
                    VALUES (?, ?, 'ESCRITURACAO_MANUAL', 'legado-v1', CAST(? AS jsonb), CAST(? AS jsonb),
                        TIMESTAMPTZ '2026-11-12 15:00:00Z', 'PENDENTE')
                    """)) {
                pedido.setObject(1, id);
                pedido.setObject(2, sacador.id());
                pedido.setString(3, dados);
                pedido.setString(4, fotografia);
                pedido.executeUpdate();
            }
        }
        var v2 = Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .schemas("escrituradora").load();
        assertThat(v2.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(v2.info().current().getVersion().toString()).isEqualTo("2");
        var historicoV1 = Arrays.stream(v2.info().all())
                .filter(migracao -> migracao.getVersion() != null && migracao.getVersion().toString().equals("1"))
                .findFirst().orElseThrow();
        assertThat(historicoV1.getChecksum()).isEqualTo(checksum);
        v2.validate();
        assertThat(v2.migrate().migrationsExecuted).isZero();
        try (var conexao = DriverManager.getConnection(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             var comando = conexao.prepareStatement("SELECT * FROM escrituradora.processamento_escrituracao WHERE id = ?")) {
            comando.setObject(1, id);
            try (var resultado = comando.executeQuery()) {
                assertThat(resultado.next()).isTrue();
                assertThat(resultado.getString("estado")).isEqualTo("PENDENTE");
                assertThat(resultado.getInt("tentativas")).isZero();
                assertThat(resultado.getTimestamp("proxima_execucao_em")).isEqualTo(resultado.getTimestamp("recebido_em"));
                assertThat(resultado.getString("chave_idempotencia")).isEqualTo("legado-v1");
                assertThat(json.readTree(resultado.getString("dados"))).isEqualTo(json.readTree(dados));
                assertThat(json.readTree(resultado.getString("sacador_no_recebimento"))).isEqualTo(json.readTree(fotografia));
                assertThat(resultado.getObject("id_duplicata")).isNull();
                assertThat(resultado.getObject("reserva_expira_em")).isNull();
                assertThat(resultado.getObject("finalizado_em")).isNull();
            }
        }
    }
}
