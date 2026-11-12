package br.com.duplicataescritural.signataria.escrituracao.aplicacao;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ConflitoIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.DadosEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.FotografiaSacador;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.InsercaoIdempotenteProcessamento;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.ProcessamentosEscrituracao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoEscrituracao {

    private final ProcessamentosEscrituracao processamentos;
    private final InsercaoIdempotenteProcessamento insercao;

    public ServicoEscrituracao(ProcessamentosEscrituracao processamentos, InsercaoIdempotenteProcessamento insercao) {
        this.processamentos = processamentos;
        this.insercao = insercao;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProcessamentoEscrituracao receber(FotografiaSacador sacador, ChaveIdempotencia chave, DadosEscrituracao dados) {
        var existente = processamentos.findBySacadorIdAndOperacaoAndChaveIdempotencia(
                sacador.id(), ProcessamentoEscrituracao.OPERACAO, chave.valor());
        if (existente.isPresent()) {
            return conferirRepeticao(existente.get(), dados);
        }
        dados.validarRecebimento(sacador.inicioOperacao(), LocalDate.now(ZoneOffset.UTC));
        insercao.inserirSeAusente(sacador, chave.valor(), dados);
        return conferirRepeticao(processamentos.findBySacadorIdAndOperacaoAndChaveIdempotencia(
                sacador.id(), ProcessamentoEscrituracao.OPERACAO, chave.valor()).orElseThrow(), dados);
    }

    @Transactional(readOnly = true)
    public Optional<ProcessamentoEscrituracao> consultar(UUID id, UUID sacadorId) {
        return processamentos.findByIdAndSacadorId(id, sacadorId);
    }

    private ProcessamentoEscrituracao conferirRepeticao(ProcessamentoEscrituracao existente, DadosEscrituracao dados) {
        if (!existente.dados().equals(dados)) {
            throw new ConflitoIdempotencia();
        }
        return existente;
    }
}
