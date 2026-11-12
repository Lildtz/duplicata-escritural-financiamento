package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;

public record DadosEscrituracao(Sacado sacado, String tipoDuplicata, String numeroDuplicata,
                                String numeroFatura, Integer numeroParcela, LocalDate dataFaturamento,
                                LocalDate dataVencimento, BigDecimal valorFatura,
                                BigDecimal valorDuplicata, DocumentoFiscal documentoFiscal) {

    public DadosEscrituracao {
        if (sacado == null || documentoFiscal == null || dataFaturamento == null || dataVencimento == null) {
            throw new IllegalArgumentException("Partes, documento fiscal e datas são obrigatórios");
        }
        if (!"MERCANTIL".equals(tipoDuplicata) || !Integer.valueOf(1).equals(numeroParcela)) {
            throw new IllegalArgumentException("Esta entrega aceita somente duplicata mercantil de parcela única (1)");
        }
        numeroDuplicata = Endereco.texto(numeroDuplicata, "Número da duplicata", 60);
        numeroFatura = Endereco.texto(numeroFatura, "Número da fatura", 60);
        valorFatura = valor(valorFatura);
        valorDuplicata = valor(valorDuplicata);
        if (valorFatura.compareTo(valorDuplicata) != 0) {
            throw new IllegalArgumentException("Parcela única sem ajustes deve corresponder ao valor da fatura");
        }
        if (dataVencimento.isBefore(dataFaturamento)) {
            throw new IllegalArgumentException("Vencimento anterior ao faturamento");
        }
    }

    public void validarRecebimento(LocalDate inicioOperacao, LocalDate hoje) {
        if (dataFaturamento.isBefore(inicioOperacao) || dataFaturamento.isAfter(hoje)) {
            throw new IllegalArgumentException("Faturamento fora do período operacional do Sacador");
        }
    }

    private static BigDecimal valor(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw new IllegalArgumentException("Valor deve ser positivo e possuir até 12 inteiros e 2 decimais");
        }
        try {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Valor não pode conter fração de centavo");
        }
    }

    public record Sacado(String cnpj, String nome, Endereco domicilio) {
        public Sacado {
            cnpj = new Cnpj(cnpj).valor();
            nome = Endereco.texto(nome, "Nome do Sacado", 150);
            if (domicilio == null) {
                throw new IllegalArgumentException("Domicílio do Sacado obrigatório");
            }
        }
    }

    public record DocumentoFiscal(String tipo, String numero, String serie) {
        public DocumentoFiscal {
            if (!"NFE".equals(tipo)) {
                throw new IllegalArgumentException("Esta entrega aceita somente referência de NF-e (NFE)");
            }
            numero = numero(numero, 9);
            serie = numero(serie, 3);
        }

        private static String numero(String valor, int tamanho) {
            valor = Endereco.texto(valor, "Número ou série fiscal", tamanho);
            if (!valor.matches("[0-9]+")) {
                throw new IllegalArgumentException("Número e série fiscal devem ser numéricos");
            }
            return new BigInteger(valor).toString();
        }
    }
}
