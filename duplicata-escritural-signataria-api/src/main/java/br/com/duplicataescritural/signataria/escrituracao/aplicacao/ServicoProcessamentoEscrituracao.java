package br.com.duplicataescritural.signataria.escrituracao.aplicacao;

import br.com.duplicataescritural.signataria.observabilidade.Observabilidade;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import br.com.duplicataescritural.signataria.escrituracao.dominio.CodigoResultado;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Duplicata;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.RejeicaoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ReservaExecucao;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.DuplicataRepository;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.ProcessamentoEscrituracaoRepository;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.SacadorRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ServicoProcessamentoEscrituracao {
    private static final Set<String> UNICIDADES_COMERCIAIS = Set.of("uk_duplicata_sacador_numero",
            "uk_duplicata_sacador_fatura_parcela");

    private final ProcessamentoEscrituracaoRepository processamentos;
    private final DuplicataRepository duplicatas;
    private final SacadorRepository sacadores;
    private final Clock relogio;
    private final ConfiguracaoProcessamento configuracao;
    private final TransactionTemplate transacao;
    private final Observabilidade observabilidade;

    public ServicoProcessamentoEscrituracao(ProcessamentoEscrituracaoRepository processamentos, DuplicataRepository duplicatas,
            SacadorRepository sacadores, Clock relogio, ConfiguracaoProcessamento configuracao,
            PlatformTransactionManager transactionManager, Observabilidade observabilidade) {
        this.observabilidade = observabilidade;
        this.processamentos = processamentos;
        this.duplicatas = duplicatas;
        this.sacadores = sacadores;
        this.relogio = relogio;
        this.configuracao = configuracao;
        this.transacao = new TransactionTemplate(transactionManager);
        transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transacao.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transacao.setTimeout(Math.toIntExact(configuracao.timeoutExecucao().toSeconds()));
    }

    public void processarProximo() {
        reservarProximo().ifPresent(reserva -> {
            try {
                observabilidade.observar("executar_escrituracao", () -> { executar(reserva); return null; });
            } catch (RuntimeException exception) {
                // execute só retorna/lança após commit/rollback: T3 nunca reutiliza uma transação abortada.
                registrarFalha(reserva, exception);
            }
        });
    }

    public Optional<ReservaExecucao> reservarProximo() {
        return transacao.execute(status -> {
            var agora = relogio.instant();
            var elegivel = processamentos.buscarElegivel(agora);
            if (elegivel.isEmpty()) {
                return Optional.empty();
            }
            var processamento = elegivel.get();
            if (processamento.estado() == ProcessamentoEscrituracao.Estado.EM_PROCESSAMENTO) {
                var abandonada = new ReservaExecucao(processamento.id(), processamento.tentativas());
                processamento.falhar(abandonada, agora, true, configuracao.maximoTentativas(),
                        configuracao.esperaApos(abandonada.tentativa()), CodigoResultado.RESERVA_EXPIRADA);
                observabilidade.transicaoAposCommit(processamento);
                return Optional.empty();
            }
            if (processamento.tentativas() >= configuracao.maximoTentativas()) {
                processamento.esgotarTentativas(agora, configuracao.maximoTentativas());
                observabilidade.transicaoAposCommit(processamento);
                return Optional.empty();
            }
            var reserva = processamento.reservar(agora, configuracao.duracaoReserva(), configuracao.maximoTentativas());
            observabilidade.transicaoAposCommit(processamento);
            return Optional.of(reserva);
        });
    }

    public void executar(ReservaExecucao reserva) {
        transacao.executeWithoutResult(status -> {
            var processamento = processamentos.bloquear(reserva.idProcessamento()).orElseThrow();
            if (!processamento.reservaValida(reserva, relogio.instant())) {
                return;
            }
            var sacador = sacadores.findById(processamento.sacadorId()).orElseThrow();
            if (!sacador.habilitado()) {
                throw new RejeicaoEscrituracao(CodigoResultado.SACADOR_NAO_HABILITADO);
            }
            var duplicata = Duplicata.escriturar(processamento, relogio.instant());
            duplicatas.saveAndFlush(duplicata);
            var agora = relogio.instant();
            if (!processamento.reservaValida(reserva, agora)) {
                throw new TransactionTimedOutException("Reserva expirada durante a execução");
            }
            processamento.concluir(reserva, duplicata, agora);
            processamentos.flush();
            observabilidade.transicaoAposCommit(processamento);
        });
    }

    public void registrarFalha(ReservaExecucao reserva, RuntimeException exception) {
        var codigo = classificar(exception);
        transacao.executeWithoutResult(status -> {
            var processamento = processamentos.bloquear(reserva.idProcessamento()).orElseThrow();
            var agora = relogio.instant();
            if (!processamento.reservaValida(reserva, agora)) {
                return;
            }
            if (codigo.negocio()) {
                processamento.rejeitar(reserva, codigo, agora);
            } else {
                processamento.falhar(reserva, agora, codigo == CodigoResultado.FALHA_TRANSITORIA,
                        configuracao.maximoTentativas(), configuracao.esperaApos(reserva.tentativa()), codigo);
            }
            observabilidade.transicaoAposCommit(processamento);
        });
    }

    private CodigoResultado classificar(RuntimeException exception) {
        boolean transitoria = false;
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof RejeicaoEscrituracao rejeicao) {
                return rejeicao.codigo();
            }
            if (causa instanceof ConstraintViolationException violacao
                    && violacao.getConstraintName() != null
                    && UNICIDADES_COMERCIAIS.contains(violacao.getConstraintName())) {
                return CodigoResultado.DUPLICATA_JA_ESCRITURADA;
            }
            transitoria |= causa instanceof TransientDataAccessException
                    || causa instanceof RecoverableDataAccessException
                    || causa instanceof TransactionTimedOutException
                    || causa instanceof SQLTransientException || causa instanceof SQLRecoverableException
                    || (causa instanceof SQLException sql && sql.getSQLState() != null
                        && (sql.getSQLState().startsWith("08") || sql.getSQLState().equals("40001")
                            || sql.getSQLState().equals("40P01")));
        }
        return transitoria ? CodigoResultado.FALHA_TRANSITORIA : CodigoResultado.ERRO_TECNICO;
    }
}
