package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.util.Locale;

public record Cnpj(String valor) {

    public Cnpj {
        if (valor == null) {
            throw new IllegalArgumentException("CNPJ obrigatório");
        }
        valor = valor.strip();
        if (!valor.matches("[0-9A-Za-z]{12}[0-9]{2}")
                && !valor.matches("[0-9A-Za-z]{2}\\.[0-9A-Za-z]{3}\\.[0-9A-Za-z]{3}/[0-9A-Za-z]{4}-[0-9]{2}")) {
            throw new IllegalArgumentException("Formato de CNPJ inválido");
        }
        valor = valor.replace(".", "").replace("/", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (valor.chars().distinct().count() == 1
                || digito(valor.substring(0, 12)) != valor.charAt(12) - '0'
                || digito(valor.substring(0, 13)) != valor.charAt(13) - '0') {
            throw new IllegalArgumentException("Dígitos verificadores do CNPJ inválidos");
        }
    }

    private static int digito(String base) {
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - 48) * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
