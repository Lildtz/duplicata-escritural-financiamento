package br.com.duplicataescritural.signataria.escrituracao.dominio;

public record ChaveIdempotencia(String valor) {
    public ChaveIdempotencia {
        if (valor == null || !valor.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Idempotency-Key inválida ou ausente");
        }
    }
}
