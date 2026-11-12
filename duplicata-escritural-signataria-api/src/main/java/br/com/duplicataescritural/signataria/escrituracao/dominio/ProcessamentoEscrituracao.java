package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
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
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "proxima_execucao_em", nullable = false)
    private Instant proximaExecucaoEm;

    @Column(name = "reserva_expira_em")
    private Instant reservaExpiraEm;

    @Column(name = "id_duplicata")
    private UUID idDuplicata;

    @Column(name = "escriturada_em")
    private Instant escrituradaEm;

    @Column(name = "finalizado_em")
    private Instant finalizadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "codigo_resultado", length = 40)
    private CodigoResultado codigoResultado;

    protected ProcessamentoEscrituracao() { }

    public UUID id() { return id; }
    public UUID sacadorId() { return sacadorId; }
    public String chaveIdempotencia() { return chaveIdempotencia; }
    public DadosEscrituracao dados() { return dados; }
    public FotografiaSacador sacadorNoRecebimento() { return sacadorNoRecebimento; }
    public Instant recebidoEm() { return recebidoEm; }
    public Estado estado() { return estado; }
    public int tentativas() { return tentativas; }
    public Instant proximaExecucaoEm() { return proximaExecucaoEm; }
    public Instant reservaExpiraEm() { return reservaExpiraEm; }
    public UUID idDuplicata() { return idDuplicata; }
    public Instant escrituradaEm() { return escrituradaEm; }
    public Instant finalizadoEm() { return finalizadoEm; }
    public CodigoResultado codigoResultado() { return codigoResultado; }

    public ReservaExecucao reservar(Instant agora, Duration duracao, int maximoTentativas) {
        if (estado != Estado.PENDENTE || proximaExecucaoEm.isAfter(agora)
                || tentativas >= maximoTentativas || duracao.isNegative() || duracao.isZero()) {
            throw new IllegalStateException("Processamento não elegível para reserva");
        }
        estado = Estado.EM_PROCESSAMENTO;
        tentativas++;
        reservaExpiraEm = agora.plus(duracao);
        return new ReservaExecucao(id, tentativas);
    }

    public boolean tentativaAtual(ReservaExecucao reserva) {
        return id.equals(reserva.idProcessamento()) && estado == Estado.EM_PROCESSAMENTO
                && tentativas == reserva.tentativa();
    }

    public boolean reservaValida(ReservaExecucao reserva, Instant agora) {
        return tentativaAtual(reserva) && reservaExpiraEm.isAfter(agora);
    }

    public void concluir(ReservaExecucao reserva, Duplicata duplicata, Instant agora) {
        exigirReserva(reserva, agora);
        if (!sacadorId.equals(duplicata.sacadorId()) || !dados.equals(duplicata.dados())) {
            throw new IllegalArgumentException("Duplicata não corresponde à solicitação");
        }
        idDuplicata = duplicata.id();
        escrituradaEm = duplicata.escrituradaEm();
        finalizar(Estado.CONCLUIDO, agora, null);
    }

    public void rejeitar(ReservaExecucao reserva, CodigoResultado codigo, Instant agora) {
        exigirReserva(reserva, agora);
        if (!Objects.requireNonNull(codigo).negocio()) {
            throw new IllegalArgumentException("Rejeição exige código de negócio");
        }
        finalizar(Estado.REJEITADO, agora, codigo);
    }

    public void falhar(ReservaExecucao reserva, Instant agora, boolean transitoria,
                       int maximoTentativas, Duration espera, CodigoResultado codigo) {
        exigirTentativa(reserva);
        // Somente a recuperação de abandono pode registrar uma falha após a expiração.
        if (codigo == CodigoResultado.RESERVA_EXPIRADA) {
            if (reservaExpiraEm.isAfter(agora)) {
                throw new IllegalStateException("Reserva ainda não expirou");
            }
        } else {
            exigirReserva(reserva, agora);
        }
        if (Objects.requireNonNull(codigo).negocio() || espera.isNegative()) {
            throw new IllegalArgumentException("Falha técnica exige código técnico e espera válida");
        }
        if (!transitoria || tentativas >= maximoTentativas) {
            finalizar(Estado.FALHA_TECNICA, agora, codigo);
        } else {
            estado = Estado.PENDENTE;
            proximaExecucaoEm = agora.plus(espera);
            reservaExpiraEm = null;
            codigoResultado = codigo;
        }
    }

    public void esgotarTentativas(Instant agora, int maximoTentativas) {
        if (estado != Estado.PENDENTE || tentativas < maximoTentativas) {
            throw new IllegalStateException("Orçamento de tentativas não esgotado");
        }
        finalizar(Estado.FALHA_TECNICA, agora, CodigoResultado.TENTATIVAS_ESGOTADAS);
    }

    private void exigirReserva(ReservaExecucao reserva, Instant agora) {
        if (!reservaValida(reserva, agora)) {
            throw new IllegalStateException("Reserva inválida ou expirada");
        }
    }

    private void exigirTentativa(ReservaExecucao reserva) {
        if (!tentativaAtual(reserva)) {
            throw new IllegalStateException("Tentativa não é a atual");
        }
    }

    private void finalizar(Estado destino, Instant agora, CodigoResultado codigo) {
        estado = destino;
        finalizadoEm = Objects.requireNonNull(agora);
        codigoResultado = codigo;
        reservaExpiraEm = null;
    }

    public enum Estado { PENDENTE, EM_PROCESSAMENTO, CONCLUIDO, REJEITADO, FALHA_TECNICA }
}
