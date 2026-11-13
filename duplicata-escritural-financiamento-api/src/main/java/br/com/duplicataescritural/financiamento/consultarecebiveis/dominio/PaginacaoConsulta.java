package br.com.duplicataescritural.financiamento.consultarecebiveis.dominio;

public record PaginacaoConsulta(int pagina, int tamanho) {
    public PaginacaoConsulta {
        if (pagina < 0 || tamanho < 1 || tamanho > 100 || (long) pagina * tamanho > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Paginação inválida");
        }
    }
}
