package br.com.duplicataescritural.signataria.escrituracao.dominio;

public class RejeicaoEscrituracao extends RuntimeException {
    private final CodigoResultado codigo;

    public RejeicaoEscrituracao(CodigoResultado codigo) {
        super(codigo.motivo());
        if (!codigo.negocio()) {
            throw new IllegalArgumentException("Rejeição exige código de negócio");
        }
        this.codigo = codigo;
    }

    public CodigoResultado codigo() { return codigo; }
}
