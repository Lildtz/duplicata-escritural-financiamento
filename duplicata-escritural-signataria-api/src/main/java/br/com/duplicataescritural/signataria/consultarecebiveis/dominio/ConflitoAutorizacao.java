package br.com.duplicataescritural.signataria.consultarecebiveis.dominio;

public class ConflitoAutorizacao extends RuntimeException {
    private final String codigo;

    public ConflitoAutorizacao(String codigo) {
        super("Operação incompatível com a autorização de consulta");
        this.codigo = codigo;
    }

    public String codigo() { return codigo; }
}
