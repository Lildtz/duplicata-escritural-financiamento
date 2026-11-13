package br.com.duplicataescritural.financiamento.consultarecebiveis.dominio;

public class FalhaRegistradora extends RuntimeException {
    public enum Motivo {
        AUTENTICACAO_REJEITADA, ACESSO_NEGADO, RECURSO_NAO_ENCONTRADO, PARAMETROS_INVALIDOS,
        CONFLITO, TIMEOUT, INDISPONIVEL, ERRO_REMOTO, RESPOSTA_INCOMPATIVEL
    }

    private final Motivo motivo;

    public FalhaRegistradora(Motivo motivo) {
        // Não preserva corpo, URL, cabeçalhos ou causas HTTP que possam conter dados sensíveis.
        super("Falha na integração com a Registradora: " + motivo);
        this.motivo = motivo;
    }

    public Motivo motivo() { return motivo; }
}
