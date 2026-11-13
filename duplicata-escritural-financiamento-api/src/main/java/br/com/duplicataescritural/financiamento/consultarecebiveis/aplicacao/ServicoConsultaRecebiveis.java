package br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.GatewayRegistradora;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginaDuplicatas;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.SolicitacaoAutorizacao;

public class ServicoConsultaRecebiveis {
    private final GatewayRegistradora registradora;
    private final Clock relogio;

    public ServicoConsultaRecebiveis(GatewayRegistradora registradora, Clock relogio) {
        this.registradora = registradora;
        this.relogio = relogio;
    }

    public AutorizacaoConsulta solicitarAutorizacao(String cnpjSacador, Instant fimVigencia, String chaveIdempotencia) {
        return registradora.solicitarAutorizacao(new SolicitacaoAutorizacao(cnpjSacador, fimVigencia, chaveIdempotencia));
    }

    public AutorizacaoConsulta consultarAutorizacao(UUID idAutorizacao) {
        return registradora.consultarAutorizacao(Objects.requireNonNull(idAutorizacao, "Autorização obrigatória"));
    }

    public ConsultaDuplicatas consultarDuplicatas(UUID idAutorizacao) {
        return consultarDuplicatas(idAutorizacao, 0, 50);
    }

    public ConsultaDuplicatas consultarDuplicatas(UUID idAutorizacao, int pagina, int tamanho) {
        Objects.requireNonNull(idAutorizacao, "Autorização obrigatória");
        var paginacao = new PaginacaoConsulta(pagina, tamanho);
        Instant consultadaEm = relogio.instant();
        return new ConsultaDuplicatas(consultadaEm, registradora.consultarDuplicatas(idAutorizacao, paginacao));
    }

    // Referência local do início da chamada; a Signatária não publica timestamp ou snapshot da consulta.
    public record ConsultaDuplicatas(Instant consultadaEm, PaginaDuplicatas resultado) { }
}
