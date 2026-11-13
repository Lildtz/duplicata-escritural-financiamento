package br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.FalhaRegistradora;

import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.GatewayRegistradora;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginaDuplicatas;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.SolicitacaoAutorizacao;

public class ServicoConsultaRecebiveis {
    private static final Logger LOG = LoggerFactory.getLogger(ServicoConsultaRecebiveis.class);
    private final ObservationRegistry observacoes;
    private final GatewayRegistradora registradora;
    private final Clock relogio;

    public ServicoConsultaRecebiveis(GatewayRegistradora registradora, Clock relogio, ObservationRegistry observacoes) {
        this.registradora = registradora;
        this.relogio = relogio;
        this.observacoes = observacoes;
    }

    public AutorizacaoConsulta solicitarAutorizacao(String cnpjSacador, Instant fimVigencia, String chaveIdempotencia) {
        var solicitacao = new SolicitacaoAutorizacao(cnpjSacador, fimVigencia, chaveIdempotencia);
        return observar("solicitar_autorizacao", () -> registradora.solicitarAutorizacao(solicitacao));
    }

    public AutorizacaoConsulta consultarAutorizacao(UUID idAutorizacao) {
        Objects.requireNonNull(idAutorizacao, "Autorização obrigatória");
        return observar("consultar_autorizacao", () -> registradora.consultarAutorizacao(idAutorizacao));
    }

    public ConsultaDuplicatas consultarDuplicatas(UUID idAutorizacao) {
        return consultarDuplicatas(idAutorizacao, 0, 50);
    }

    public ConsultaDuplicatas consultarDuplicatas(UUID idAutorizacao, int pagina, int tamanho) {
        Objects.requireNonNull(idAutorizacao, "Autorização obrigatória");
        var paginacao = new PaginacaoConsulta(pagina, tamanho);
        Instant consultadaEm = relogio.instant();
        return observar("consultar_duplicatas", () -> new ConsultaDuplicatas(consultadaEm,
                registradora.consultarDuplicatas(idAutorizacao, paginacao)));
    }

    private <T> T observar(String operacao, Supplier<T> chamada) {
        var observacao = Observation.createNotStarted("financiamento.registradora.operacao", observacoes)
                .lowCardinalityKeyValue("operacao", operacao).start();
        String resultado = "sucesso";
        String motivo = "nenhum";
        try (var scope = observacao.openScope()) {
            try {
                return chamada.get();
            } catch (FalhaRegistradora falha) {
                motivo = falha.motivo().name();
                resultado = switch (falha.motivo()) {
                    case AUTENTICACAO_REJEITADA, ACESSO_NEGADO, RECURSO_NAO_ENCONTRADO,
                            PARAMETROS_INVALIDOS, CONFLITO -> "rejeitado";
                    default -> "falha";
                };
                observacao.error(new IllegalStateException(motivo));
                throw falha;
            } catch (RuntimeException falha) {
                resultado = "falha";
                motivo = "ERRO_INTERNO";
                observacao.error(new IllegalStateException(motivo));
                throw falha;
            } finally {
                observacao.lowCardinalityKeyValue("resultado", resultado).lowCardinalityKeyValue("motivo", motivo);
                LOG.atInfo().addKeyValue("operacao", operacao).addKeyValue("resultado", resultado)
                        .addKeyValue("motivo", motivo).log("Operação da registradora finalizada");
            }
        } finally {
            observacao.stop();
        }
    }

    // Referência local do início da chamada; a Signatária não publica timestamp ou snapshot da consulta.
    public record ConsultaDuplicatas(Instant consultadaEm, PaginaDuplicatas resultado) { }
}
