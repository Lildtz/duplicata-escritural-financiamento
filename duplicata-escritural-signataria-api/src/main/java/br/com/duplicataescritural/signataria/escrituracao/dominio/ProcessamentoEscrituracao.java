package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "processamento_escrituracao", schema = "escrituradora")
public class ProcessamentoEscrituracao {

    public static final String OPERACAO = "ESCRITURACAO_MANUAL";

    @Id
    private UUID id;

    @Column(name = "sacador_id", nullable = false, updatable = false)
    private UUID sacadorId;

    @Column(nullable = false, length = 30, updatable = false)
    private String operacao;

    @Column(name = "chave_idempotencia", nullable = false, length = 128, updatable = false)
    private String chaveIdempotencia;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private DadosEscrituracao dados;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sacador_no_recebimento", nullable = false, columnDefinition = "jsonb", updatable = false)
    private FotografiaSacador sacadorNoRecebimento;

    @Column(name = "recebido_em", nullable = false, updatable = false)
    private Instant recebidoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Estado estado;

    protected ProcessamentoEscrituracao() { }

    public UUID id() { return id; }
    public UUID sacadorId() { return sacadorId; }
    public String chaveIdempotencia() { return chaveIdempotencia; }
    public DadosEscrituracao dados() { return dados; }
    public FotografiaSacador sacadorNoRecebimento() { return sacadorNoRecebimento; }
    public Instant recebidoEm() { return recebidoEm; }
    public Estado estado() { return estado; }

    public enum Estado { PENDENTE }
}
