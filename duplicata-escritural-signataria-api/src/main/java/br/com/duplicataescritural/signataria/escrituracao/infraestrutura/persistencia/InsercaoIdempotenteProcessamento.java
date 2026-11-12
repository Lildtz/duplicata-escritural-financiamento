package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.DadosEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.FotografiaSacador;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class InsercaoIdempotenteProcessamento {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public InsercaoIdempotenteProcessamento(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void inserirSeAusente(FotografiaSacador sacador, String chave, DadosEscrituracao dados) {
        // ON CONFLICT mantém a transação utilizável quando duas requisições disputam a mesma chave.
        jdbc.sql("""
                INSERT INTO escrituradora.processamento_escrituracao
                    (id, sacador_id, operacao, chave_idempotencia, dados, sacador_no_recebimento, recebido_em, estado)
                VALUES (:id, :sacador, 'ESCRITURACAO_MANUAL', :chave, CAST(:dados AS jsonb),
                    CAST(:fotografia AS jsonb), CURRENT_TIMESTAMP, 'PENDENTE')
                ON CONFLICT ON CONSTRAINT uk_processamento_idempotencia DO NOTHING
                """).param("id", UUID.randomUUID()).param("sacador", sacador.id()).param("chave", chave)
                .param("dados", json.writeValueAsString(dados))
                .param("fotografia", json.writeValueAsString(sacador)).update();
    }
}
