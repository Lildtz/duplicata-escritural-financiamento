package br.com.duplicataescritural.signataria.escrituracao.dominio;

public class ConflitoIdempotencia extends RuntimeException {
    public ConflitoIdempotencia() {
        super("Chave idempotente já utilizada com outro conteúdo");
    }
}
