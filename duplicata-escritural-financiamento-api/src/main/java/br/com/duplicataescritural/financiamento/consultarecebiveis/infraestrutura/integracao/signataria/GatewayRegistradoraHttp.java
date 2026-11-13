package br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.DuplicataConsultada;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.FalhaRegistradora;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.GatewayRegistradora;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginaDuplicatas;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.SolicitacaoAutorizacao;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.FalhaRegistradora.Motivo.*;

public class GatewayRegistradoraHttp implements GatewayRegistradora {
    private static final String AUTORIZACOES = "/api/v1/autorizacoes-consulta";
    private final RestClient client;

    public GatewayRegistradoraHttp(RestClient client) {
        this.client = client;
    }

    @Override
    public AutorizacaoConsulta solicitarAutorizacao(SolicitacaoAutorizacao solicitacao) {
        Objects.requireNonNull(solicitacao);
        var pedido = new PedidoAutorizacaoHttp(solicitacao.cnpjSacador(), solicitacao.fimVigencia());
        return executar(client.post().uri(AUTORIZACOES).header("Idempotency-Key", solicitacao.chaveIdempotencia())
                .contentType(MediaType.APPLICATION_JSON).body(pedido), 201, RespostaAutorizacao.class, resposta -> {
                    var autorizacao = resposta.modelo();
                    exigirResposta(autorizacao.cnpjSacador().equals(solicitacao.cnpjSacador()));
                    return autorizacao;
                });
    }

    @Override
    public AutorizacaoConsulta consultarAutorizacao(UUID idAutorizacao) {
        Objects.requireNonNull(idAutorizacao);
        return executar(client.get().uri(AUTORIZACOES + "/{id}", idAutorizacao), 200,
                RespostaAutorizacao.class, resposta -> {
                    var autorizacao = resposta.modelo();
                    exigirResposta(autorizacao.idAutorizacao().equals(idAutorizacao));
                    return autorizacao;
                });
    }

    @Override
    public PaginaDuplicatas consultarDuplicatas(UUID idAutorizacao, PaginacaoConsulta paginacao) {
        Objects.requireNonNull(idAutorizacao);
        Objects.requireNonNull(paginacao);
        return executar(client.get().uri(AUTORIZACOES + "/{id}/duplicatas?pagina={pagina}&tamanho={tamanho}",
                idAutorizacao, paginacao.pagina(), paginacao.tamanho()),
                200, RespostaDuplicatas.class, resposta -> {
                    var pagina = resposta.modelo();
                    exigirResposta(pagina.pagina() == paginacao.pagina() && pagina.tamanho() == paginacao.tamanho());
                    return pagina;
                });
    }

    private <T, R> R executar(RestClient.RequestHeadersSpec<?> pedido, int statusEsperado,
            Class<T> tipoResposta, Function<T, R> converter) {
        try {
            T resposta = pedido.retrieve().onStatus(status -> status.value() != statusEsperado,
                    (request, response) -> { throw falhaHttp(response.getStatusCode().value()); }).body(tipoResposta);
            exigirResposta(resposta != null);
            return converter.apply(resposta);
        } catch (ResourceAccessException exception) {
            for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
                if (causa instanceof HttpTimeoutException || causa instanceof SocketTimeoutException) {
                    throw new FalhaRegistradora(TIMEOUT);
                }
            }
            throw new FalhaRegistradora(INDISPONIVEL);
        } catch (RestClientException | IllegalArgumentException | NullPointerException exception) {
            throw new FalhaRegistradora(RESPOSTA_INCOMPATIVEL);
        }
    }

    private static FalhaRegistradora falhaHttp(int status) {
        var motivo = switch (status) {
            case 401 -> AUTENTICACAO_REJEITADA;
            case 403 -> ACESSO_NEGADO;
            case 404 -> RECURSO_NAO_ENCONTRADO;
            case 400, 422 -> PARAMETROS_INVALIDOS;
            case 409 -> CONFLITO;
            default -> status >= 500 && status <= 599 ? ERRO_REMOTO : RESPOSTA_INCOMPATIVEL;
        };
        return new FalhaRegistradora(motivo);
    }

    private static void exigirResposta(boolean valida) {
        if (!valida) throw new FalhaRegistradora(RESPOSTA_INCOMPATIVEL);
    }

    private record PedidoAutorizacaoHttp(String cnpjSacador, Instant fimVigencia) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespostaAutorizacao(UUID idAutorizacao, UUID financiadorId, String cnpjSacador,
            AutorizacaoConsulta.Estado status, Boolean vigente, Instant solicitadaEm, Instant inicioVigencia,
            Instant fimVigencia, Instant decididaEm, Instant revogadaEm, AutorizacaoConsulta.RevogadoPor revogadoPor) {
        AutorizacaoConsulta modelo() {
            return new AutorizacaoConsulta(idAutorizacao, financiadorId, cnpjSacador, status, vigente,
                    solicitadaEm, inicioVigencia, fimVigencia, decididaEm, revogadaEm, revogadoPor);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespostaDuplicatas(String cnpjSacador, PaginaDuplicatas.Escopo escopo, List<RespostaDuplicata> itens,
            Integer pagina, Integer tamanho, Boolean temProximaPagina) {
        PaginaDuplicatas modelo() {
            return new PaginaDuplicatas(cnpjSacador, escopo, itens.stream().map(RespostaDuplicata::modelo).toList(),
                    pagina, tamanho, temProximaPagina);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespostaDuplicata(UUID idDuplicata, String numeroDuplicata, String cnpjSacado,
            LocalDate dataVencimento, BigDecimal valorNominal, Instant escrituradaEm) {
        DuplicataConsultada modelo() {
            return new DuplicataConsultada(idDuplicata, numeroDuplicata, cnpjSacado, dataVencimento, valorNominal, escrituradaEm);
        }
    }
}
