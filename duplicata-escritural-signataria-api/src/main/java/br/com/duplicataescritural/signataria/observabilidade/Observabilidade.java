package br.com.duplicataescritural.signataria.observabilidade;

import java.util.function.Supplier;

import br.com.duplicataescritural.signataria.consultarecebiveis.aplicacao.ParametrosConsultaInvalidos;
import br.com.duplicataescritural.signataria.consultarecebiveis.aplicacao.RecursoConsultaNaoEncontrado;
import br.com.duplicataescritural.signataria.consultarecebiveis.dominio.ConflitoAutorizacao;
import br.com.duplicataescritural.signataria.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ConflitoIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.RejeicaoEscrituracao;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class Observabilidade {
    private static final Logger LOG = LoggerFactory.getLogger(Observabilidade.class);
    private final ObservationRegistry observacoes;
    private final MeterRegistry metricas;

    public Observabilidade(ObservationRegistry observacoes, MeterRegistry metricas) {
        this.observacoes = observacoes;
        this.metricas = metricas;
    }

    // Chamadores HTTP envolvem o proxy transacional: sucesso só após seu retorno/commit.
    public <T> T observar(String operacao, Supplier<T> chamada) {
        var observacao = Observation.createNotStarted("signataria.operacao", observacoes)
                .lowCardinalityKeyValue("operacao", operacao).start();
        String resultado = "sucesso";
        String motivo = "nenhum";
        String estadoAutorizacao = null;
        try (var scope = observacao.openScope()) {
            try {
                T retorno = chamada.get();
                if (retorno instanceof AutorizacaoConsulta autorizacao) estadoAutorizacao = autorizacao.estado().name();
                return retorno;
            } catch (RuntimeException falha) {
                motivo = motivo(falha);
                resultado = motivo.equals("infraestrutura") || motivo.equals("interno") ? "falha" : "rejeitado";
                observacao.error(new IllegalStateException(motivo));
                throw falha;
            } finally {
                observacao.lowCardinalityKeyValue("resultado", resultado).lowCardinalityKeyValue("motivo", motivo);
                var evento = LOG.atInfo().addKeyValue("operacao", operacao).addKeyValue("resultado", resultado)
                        .addKeyValue("motivo", motivo);
                if (estadoAutorizacao != null) evento.addKeyValue("estadoAutorizacao", estadoAutorizacao);
                evento.log("Operação da Signatária finalizada");
            }
        } finally {
            observacao.stop();
        }
    }

    public void transicaoAposCommit(ProcessamentoEscrituracao processamento) {
        var id = processamento.id();
        var estado = processamento.estado().name();
        var tentativa = processamento.tentativas();
        var motivo = processamento.codigoResultado() == null ? "nenhum" : processamento.codigoResultado().name();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                metricas.counter("signataria.escrituracao.transicoes", "estado", estado, "motivo", motivo).increment();
                LOG.atInfo().addKeyValue("processamentoId", id).addKeyValue("tentativa", tentativa)
                        .addKeyValue("estado", estado).addKeyValue("motivo", motivo)
                        .log("Transição de Escrituração confirmada");
            }
        });
    }

    public void recebimentoConfirmado(ProcessamentoEscrituracao processamento) {
        LOG.atInfo().addKeyValue("processamentoId", processamento.id())
                .log("Recebimento de Escrituração confirmado; repetição pode referir processamento existente");
    }

    private String motivo(RuntimeException falha) {
        if (falha instanceof AccessDeniedException) return "acesso_negado";
        if (falha instanceof RecursoConsultaNaoEncontrado) return "nao_encontrado";
        if (falha instanceof ConflitoAutorizacao || falha instanceof ConflitoIdempotencia) return "conflito";
        if (falha instanceof ParametrosConsultaInvalidos || falha instanceof IllegalArgumentException) return "parametros";
        if (falha instanceof RejeicaoEscrituracao) return "negocio";
        if (falha instanceof DataAccessException || falha instanceof TransactionException) return "infraestrutura";
        return "interno";
    }
}
