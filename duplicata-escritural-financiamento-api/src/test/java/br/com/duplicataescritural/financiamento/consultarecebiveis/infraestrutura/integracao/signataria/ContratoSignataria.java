package br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

final class ContratoSignataria {
    static final UUID AUTORIZACAO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String BASE = "http://localhost:8081";
    static final String CAMINHO = "/api/v1/autorizacoes-consulta/" + AUTORIZACAO;
    static final String AUTORIZACAO_ATIVA = """
            {"idAutorizacao":"00000000-0000-0000-0000-000000000001",
             "financiadorId":"00000000-0000-0000-0000-000000000002", "cnpjSacador":"11222333000181",
             "status":"ATIVA", "vigente":true, "solicitadaEm":"2030-01-01T00:00:00Z",
             "inicioVigencia":"2030-01-01T00:00:01Z", "fimVigencia":"2030-01-02T00:00:00Z",
             "decididaEm":"2030-01-01T00:00:01Z"}
            """;
    static final String PAGINA = """
            {"cnpjSacador":"11222333000181", "escopo":"LOCAL_SIMULADO",
             "itens":[{"idDuplicata":"00000000-0000-0000-0000-000000000003", "numeroDuplicata":"DUP-01",
                       "cnpjSacado":"12ABC34501DE35", "dataVencimento":"2030-02-01",
                       "valorNominal":999999999999.99, "escrituradaEm":"2030-01-01T00:00:01.123456Z"}],
             "pagina":0, "tamanho":1, "temProximaPagina":true}
            """;

    static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
