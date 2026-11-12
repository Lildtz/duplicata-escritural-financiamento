package br.com.duplicataescritural.signataria.escrituracao.dominio;

public enum CodigoResultado {
    DUPLICATA_JA_ESCRITURADA(true, "Título já escriturado neste simulador"),
    SACADOR_NAO_HABILITADO(true, "Sacador local não habilitado para escrituração"),
    SOLICITACAO_INVALIDA(true, "Solicitação incompatível com as regras locais de escrituração"),
    FALHA_TRANSITORIA(false, "Falha técnica transitória na execução"),
    RESERVA_EXPIRADA(false, "Reserva de execução expirada"),
    TENTATIVAS_ESGOTADAS(false, "Limite de tentativas técnicas atingido"),
    ERRO_TECNICO(false, "Não foi possível concluir a escrituração local");

    private final boolean negocio;
    private final String motivo;

    CodigoResultado(boolean negocio, String motivo) {
        this.negocio = negocio;
        this.motivo = motivo;
    }

    public boolean negocio() { return negocio; }
    public String motivo() { return motivo; }
}
