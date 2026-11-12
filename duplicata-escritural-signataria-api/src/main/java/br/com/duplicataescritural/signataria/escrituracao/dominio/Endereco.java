package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.util.Locale;
import java.util.Set;

public record Endereco(String logradouro, String numero, String bairro, String cep,
                       String municipioIbge, String uf) {

    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    public Endereco {
        logradouro = texto(logradouro, "Logradouro", 150);
        numero = texto(numero, "Número do endereço", 20);
        bairro = texto(bairro, "Bairro", 100);
        cep = texto(cep, "CEP", 9);
        if (!cep.matches("[0-9]{5}-?[0-9]{3}")) {
            throw new IllegalArgumentException("CEP inválido");
        }
        cep = cep.replace("-", "");
        municipioIbge = texto(municipioIbge, "Município IBGE", 7);
        if (!municipioIbge.matches("[0-9]{7}")) {
            throw new IllegalArgumentException("Código de município inválido");
        }
        uf = texto(uf, "UF", 2).toUpperCase(Locale.ROOT);
        if (!UFS.contains(uf)) {
            throw new IllegalArgumentException("UF inválida");
        }
    }

    static String texto(String valor, String campo, int tamanhoMaximo) {
        if (valor == null || valor.isBlank() || valor.strip().length() > tamanhoMaximo
                || valor.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(campo + " inválido");
        }
        return valor.strip();
    }
}
