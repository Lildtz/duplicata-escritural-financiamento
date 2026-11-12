package br.com.duplicataescritural.signataria.consultarecebiveis.dominio;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "autorizacao_consulta", schema = "escrituradora")
public class AutorizacaoConsulta {
    public static final String OPERACAO = "SOLICITAR_AUTORIZACAO_CONSULTA";
    public enum Estado { PENDENTE, ATIVA, RECUSADA, REVOGADA }
    public enum Decisao { APROVAR, RECUSAR }
    public enum RevogadoPor { SACADOR, FINANCIADOR }

    @Id
    private UUID id;
    @Column(name = "financiador_id", nullable = false, updatable = false)
    private UUID financiadorId;
    @Column(name = "sacador_id", nullable = false, updatable = false)
    private UUID sacadorId;
    @Column(name = "cnpj_sacador", nullable = false, length = 14, updatable = false)
    private Cnpj cnpjSacador;
    @Column(nullable = false, length = 40, updatable = false)
    private String operacao;
    @Column(name = "chave_idempotencia", nullable = false, length = 128, updatable = false)
    private String chaveIdempotencia;
    @Column(name = "solicitada_em", nullable = false, updatable = false)
    private Instant solicitadaEm;
    @Column(name = "fim_vigencia", nullable = false, updatable = false)
    private Instant fimVigencia;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado;
    @Column(name = "decidida_em")
    private Instant decididaEm;
    @Column(name = "inicio_vigencia")
    private Instant inicioVigencia;
    @Column(name = "revogada_em")
    private Instant revogadaEm;
    @Enumerated(EnumType.STRING)
    @Column(name = "revogado_por", length = 12)
    private RevogadoPor revogadoPor;

    protected AutorizacaoConsulta() { }

    public static Instant normalizarInstante(Instant instante) {
        if (instante == null) {
            throw new IllegalArgumentException("Instante obrigatório");
        }
        return instante.truncatedTo(ChronoUnit.MICROS);
    }

    public static AutorizacaoConsulta solicitar(UUID financiadorId, UUID sacadorId, Cnpj cnpj,
            ChaveIdempotencia chave, Instant fimVigencia, Instant agora) {
        var autorizacao = new AutorizacaoConsulta();
        autorizacao.id = UUID.randomUUID();
        autorizacao.financiadorId = Objects.requireNonNull(financiadorId);
        autorizacao.sacadorId = Objects.requireNonNull(sacadorId);
        autorizacao.cnpjSacador = Objects.requireNonNull(cnpj);
        autorizacao.operacao = OPERACAO;
        autorizacao.chaveIdempotencia = Objects.requireNonNull(chave).valor();
        autorizacao.solicitadaEm = normalizarInstante(agora);
        autorizacao.fimVigencia = normalizarInstante(fimVigencia);
        if (!autorizacao.fimVigencia.isAfter(autorizacao.solicitadaEm)) {
            throw new IllegalArgumentException("Fim da vigência deve ser futuro");
        }
        autorizacao.estado = Estado.PENDENTE;
        return autorizacao;
    }

    public void conferirRepeticao(Cnpj cnpj, Instant fim) {
        if (!cnpjSacador.equals(cnpj) || !fimVigencia.equals(normalizarInstante(fim))) {
            throw new ConflitoAutorizacao("CONFLITO_IDEMPOTENCIA");
        }
    }

    public void decidir(Decisao decisao, Instant agora) {
        Objects.requireNonNull(decisao);
        Estado destino = decisao == Decisao.APROVAR ? Estado.ATIVA : Estado.RECUSADA;
        if (estado == destino) {
            return;
        }
        if (estado != Estado.PENDENTE) {
            throw new ConflitoAutorizacao("TRANSICAO_INVALIDA");
        }
        agora = normalizarInstante(agora);
        validarInstante(agora);
        if (!agora.isBefore(fimVigencia)) {
            throw new ConflitoAutorizacao("AUTORIZACAO_EXPIRADA");
        }
        estado = destino;
        decididaEm = agora;
        if (destino == Estado.ATIVA) {
            inicioVigencia = agora;
        }
    }

    public void revogar(RevogadoPor participante, Instant agora) {
        Objects.requireNonNull(participante);
        if (estado == Estado.REVOGADA) {
            return;
        }
        if (estado == Estado.RECUSADA) {
            throw new ConflitoAutorizacao("TRANSICAO_INVALIDA");
        }
        agora = normalizarInstante(agora);
        validarInstante(agora);
        estado = Estado.REVOGADA;
        revogadaEm = agora;
        revogadoPor = participante;
    }

    private void validarInstante(Instant agora) {
        if (agora.isBefore(solicitadaEm) || (decididaEm != null && agora.isBefore(decididaEm))) {
            throw new ConflitoAutorizacao("INSTANTE_INVALIDO");
        }
    }

    public boolean vigente(Instant agora) {
        return estado == Estado.ATIVA && !agora.isBefore(inicioVigencia) && agora.isBefore(fimVigencia);
    }

    public UUID id() { return id; }
    public UUID financiadorId() { return financiadorId; }
    public UUID sacadorId() { return sacadorId; }
    public Cnpj cnpjSacador() { return cnpjSacador; }
    public String chaveIdempotencia() { return chaveIdempotencia; }
    public Estado estado() { return estado; }
    public Instant solicitadaEm() { return solicitadaEm; }
    public Instant inicioVigencia() { return inicioVigencia; }
    public Instant fimVigencia() { return fimVigencia; }
    public Instant decididaEm() { return decididaEm; }
    public Instant revogadaEm() { return revogadaEm; }
    public RevogadoPor revogadoPor() { return revogadoPor; }
}
