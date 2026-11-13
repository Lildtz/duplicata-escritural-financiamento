package br.com.duplicataescritural.financiamento.consultarecebiveis.dominio;

import java.util.List;
import java.util.Objects;

public record PaginaDuplicatas(String cnpjSacador, Escopo escopo, List<DuplicataConsultada> itens,
        int pagina, int tamanho, boolean temProximaPagina) {
    public enum Escopo { LOCAL_SIMULADO }

    public PaginaDuplicatas {
        Objects.requireNonNull(escopo);
        itens = List.copyOf(itens);
        new PaginacaoConsulta(pagina, tamanho);
        if (cnpjSacador == null || !cnpjSacador.matches("[A-Z0-9]{12}[0-9]{2}")
                || itens.size() > tamanho || (temProximaPagina && itens.size() != tamanho)) {
            throw new IllegalArgumentException("Página de Duplicatas incompatível");
        }
    }
}
