package br.com.duplicataescritural.signataria.consultarecebiveis.aplicacao;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.duplicataescritural.signataria.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.signataria.consultarecebiveis.infraestrutura.persistencia.AutorizacaoConsultaRepository;
import br.com.duplicataescritural.signataria.escrituracao.aplicacao.DuplicataConsultada;
import br.com.duplicataescritural.signataria.escrituracao.aplicacao.ServicoConsultaDuplicatas;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Sacador;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.SacadorRepository;
import br.com.duplicataescritural.signataria.seguranca.FinanciadorAutenticado;
import br.com.duplicataescritural.signataria.seguranca.ParticipanteAutenticado;
import br.com.duplicataescritural.signataria.seguranca.SacadorAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoAutorizacaoConsulta {
    private final AutorizacaoConsultaRepository autorizacoes;
    private final SacadorRepository sacadores;
    private final Clock relogio;
    private final ServicoConsultaDuplicatas consultaDuplicatas;

    public ServicoAutorizacaoConsulta(AutorizacaoConsultaRepository autorizacoes, SacadorRepository sacadores, Clock relogio,
            ServicoConsultaDuplicatas consultaDuplicatas) {
        this.autorizacoes = autorizacoes;
        this.sacadores = sacadores;
        this.relogio = relogio;
        this.consultaDuplicatas = consultaDuplicatas;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
    public DuplicatasAutorizadas consultarDuplicatas(ParticipanteAutenticado participante, UUID id, int pagina, int tamanho) {
        if (!(participante instanceof FinanciadorAutenticado financiador)) {
            throw new AccessDeniedException("Consulta exige Financiador local");
        }
        var autorizacao = autorizacoes.findByIdAndFinanciadorId(id, financiador.id())
                .orElseThrow(RecursoConsultaNaoEncontrado::new);
        if (!autorizacao.vigente(relogio.instant())) {
            throw new AccessDeniedException("Autorização sem vigência");
        }
        if (pagina < 0 || tamanho < 1 || tamanho > 100 || (long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new ParametrosConsultaInvalidos();
        }
        return new DuplicatasAutorizadas(autorizacao.cnpjSacador().valor(),
                consultaDuplicatas.consultar(autorizacao.sacadorId(), autorizacao.cnpjSacador(), pagina, tamanho));
    }

    public record DuplicatasAutorizadas(String cnpjSacador, Slice<DuplicataConsultada> duplicatas) { }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public AutorizacaoConsulta solicitar(FinanciadorAutenticado financiador, ChaveIdempotencia chave, Cnpj cnpj, Instant fim) {
        fim = AutorizacaoConsulta.normalizarInstante(fim);
        var existente = autorizacoes.findByFinanciadorIdAndOperacaoAndChaveIdempotencia(
                financiador.id(), AutorizacaoConsulta.OPERACAO, chave.valor());
        if (existente.isPresent()) {
            existente.get().conferirRepeticao(cnpj, fim);
            return existente.get();
        }
        var sacador = sacadores.findByCnpj(cnpj).filter(Sacador::habilitado)
                .orElseThrow(RecursoConsultaNaoEncontrado::new);
        var nova = AutorizacaoConsulta.solicitar(financiador.id(), sacador.id(), cnpj, chave, fim, relogio.instant());
        autorizacoes.inserirSeAusente(nova.id(), nova.financiadorId(), nova.sacadorId(), cnpj.valor(), chave.valor(),
                nova.solicitadaEm(), nova.fimVigencia());
        var vencedora = autorizacoes.findByFinanciadorIdAndOperacaoAndChaveIdempotencia(
                financiador.id(), AutorizacaoConsulta.OPERACAO, chave.valor()).orElseThrow();
        vencedora.conferirRepeticao(cnpj, fim);
        return vencedora;
    }

    @Transactional(readOnly = true, timeout = 10)
    public AutorizacaoConsulta consultar(ParticipanteAutenticado participante, UUID id) {
        Optional<AutorizacaoConsulta> encontrada = switch (participante) {
            case FinanciadorAutenticado financiador -> autorizacoes.findByIdAndFinanciadorId(id, financiador.id());
            case SacadorAutenticado sacador -> autorizacoes.findByIdAndSacadorId(id, sacador.sacador().id());
        };
        return encontrada.orElseThrow(RecursoConsultaNaoEncontrado::new);
    }

    @Transactional(timeout = 10)
    public AutorizacaoConsulta decidir(SacadorAutenticado sacador, UUID id, AutorizacaoConsulta.Decisao decisao) {
        var autorizacao = autorizacoes.bloquearDoSacador(id, sacador.sacador().id())
                .orElseThrow(RecursoConsultaNaoEncontrado::new);
        sacadores.findById(autorizacao.sacadorId()).filter(Sacador::habilitado)
                .filter(atual -> atual.cnpj().equals(autorizacao.cnpjSacador()))
                .orElseThrow(RecursoConsultaNaoEncontrado::new);
        autorizacao.decidir(decisao, relogio.instant());
        return autorizacao;
    }

    @Transactional(timeout = 10)
    public void revogar(ParticipanteAutenticado participante, UUID id) {
        AutorizacaoConsulta autorizacao = (switch (participante) {
            case FinanciadorAutenticado financiador -> autorizacoes.bloquearDoFinanciador(id, financiador.id());
            case SacadorAutenticado sacador -> autorizacoes.bloquearDoSacador(id, sacador.sacador().id());
        }).orElseThrow(RecursoConsultaNaoEncontrado::new);
        var papel = participante instanceof FinanciadorAutenticado
                ? AutorizacaoConsulta.RevogadoPor.FINANCIADOR : AutorizacaoConsulta.RevogadoPor.SACADOR;
        autorizacao.revogar(papel, relogio.instant());
    }
}
