package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "duplicata", schema = "escrituradora")
public class Duplicata {
    @Id
    private UUID id;

    @Column(name = "sacador_id", nullable = false, updatable = false)
    private UUID sacadorId;

    @Column(name = "sacador_cnpj", nullable = false, length = 14, updatable = false)
    private Cnpj sacadorCnpj;

    @Column(name = "numero_duplicata", nullable = false, length = 60, updatable = false)
    private String numeroDuplicata;

    @Column(name = "numero_fatura", nullable = false, length = 60, updatable = false)
    private String numeroFatura;

    @Column(name = "numero_parcela", nullable = false, updatable = false)
    private int numeroParcela;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private DadosEscrituracao dados;

    @Column(name = "escriturada_em", nullable = false, updatable = false)
    private Instant escrituradaEm;

    protected Duplicata() { }

    public static Duplicata escriturar(ProcessamentoEscrituracao processamento, Instant agora) {
        var sacador = processamento.sacadorNoRecebimento();
        if (!sacador.habilitado() || !processamento.sacadorId().equals(sacador.id())) {
            throw new RejeicaoEscrituracao(CodigoResultado.SACADOR_NAO_HABILITADO);
        }
        var dados = processamento.dados();
        try {
            dados.validarRecebimento(sacador.inicioOperacao(),
                    processamento.recebidoEm().atOffset(ZoneOffset.UTC).toLocalDate());
        } catch (IllegalArgumentException exception) {
            throw new RejeicaoEscrituracao(CodigoResultado.SOLICITACAO_INVALIDA);
        }
        var duplicata = new Duplicata();
        // UUID técnico local; a geração do IUD regulatório permanece fora desta entrega.
        duplicata.id = UUID.randomUUID();
        duplicata.sacadorId = sacador.id();
        duplicata.sacadorCnpj = sacador.cnpj();
        duplicata.numeroDuplicata = dados.numeroDuplicata();
        duplicata.numeroFatura = dados.numeroFatura();
        duplicata.numeroParcela = dados.numeroParcela();
        duplicata.dados = dados;
        duplicata.escrituradaEm = Objects.requireNonNull(agora);
        return duplicata;
    }

    public UUID id() { return id; }
    public UUID sacadorId() { return sacadorId; }
    public Cnpj sacadorCnpj() { return sacadorCnpj; }
    public DadosEscrituracao dados() { return dados; }
    public Instant escrituradaEm() { return escrituradaEm; }
}
