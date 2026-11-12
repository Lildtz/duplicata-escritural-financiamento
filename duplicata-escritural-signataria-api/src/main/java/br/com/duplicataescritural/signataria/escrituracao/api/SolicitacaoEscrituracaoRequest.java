package br.com.duplicataescritural.signataria.escrituracao.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.duplicataescritural.signataria.escrituracao.dominio.DadosEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Endereco;

public record SolicitacaoEscrituracaoRequest(SacadoRequest sacado, String tipoDuplicata, String numeroDuplicata,
                                           String numeroFatura, Integer numeroParcela, LocalDate dataFaturamento,
                                           LocalDate dataVencimento, BigDecimal valorFatura,
                                           BigDecimal valorDuplicata, DocumentoFiscalRequest documentoFiscal) {

    public DadosEscrituracao paraDominio() {
        return new DadosEscrituracao(sacado == null ? null : sacado.paraDominio(), tipoDuplicata, numeroDuplicata,
                numeroFatura, numeroParcela, dataFaturamento, dataVencimento, valorFatura, valorDuplicata,
                documentoFiscal == null ? null : documentoFiscal.paraDominio());
    }

    public record SacadoRequest(String cnpj, String nome, EnderecoRequest domicilio) {
        DadosEscrituracao.Sacado paraDominio() {
            return new DadosEscrituracao.Sacado(cnpj, nome, domicilio == null ? null : domicilio.paraDominio());
        }
    }

    public record EnderecoRequest(String logradouro, String numero, String bairro, String cep,
                                  String municipioIbge, String uf) {
        Endereco paraDominio() {
            return new Endereco(logradouro, numero, bairro, cep, municipioIbge, uf);
        }
    }

    public record DocumentoFiscalRequest(String tipo, String numero, String serie) {
        DadosEscrituracao.DocumentoFiscal paraDominio() {
            return new DadosEscrituracao.DocumentoFiscal(tipo, numero, serie);
        }
    }
}
