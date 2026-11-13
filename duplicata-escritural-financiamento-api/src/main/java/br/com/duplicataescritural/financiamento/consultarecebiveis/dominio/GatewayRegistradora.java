package br.com.duplicataescritural.financiamento.consultarecebiveis.dominio;

import java.util.UUID;

public interface GatewayRegistradora {
    AutorizacaoConsulta solicitarAutorizacao(SolicitacaoAutorizacao solicitacao);
    AutorizacaoConsulta consultarAutorizacao(UUID idAutorizacao);
    PaginaDuplicatas consultarDuplicatas(UUID idAutorizacao, PaginacaoConsulta paginacao);
}
