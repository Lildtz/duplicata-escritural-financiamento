package br.com.duplicataescritural.signataria.escrituracao.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.duplicataescritural.signataria.escrituracao.dominio.DadosEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Endereco;
import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "Simplificação: mercantil, pessoa jurídica, NF-e e parcela única sem ajustes. "
                + "Textos são aparados, sem controles; campos desconhecidos são rejeitados. Sacador vem do token, não do corpo.")
public record SolicitacaoEscrituracaoRequest(
        @Schema(requiredMode = REQUIRED) SacadoRequest sacado,
        @Schema(requiredMode = REQUIRED, allowableValues = "MERCANTIL") String tipoDuplicata,
        @Schema(requiredMode = REQUIRED, description = "Não vazio; até 60 caracteres após aparar espaços; preserva zeros e maiúsculas.", example = "DUP-1") String numeroDuplicata,
        @Schema(requiredMode = REQUIRED, description = "Não vazio; até 60 caracteres após aparar espaços.", example = "FAT-1") String numeroFatura,
        @Schema(requiredMode = REQUIRED, minimum = "1", maximum = "1") Integer numeroParcela,
        @Schema(requiredMode = REQUIRED, description = "Entre início operacional do Sacador e hoje UTC.", example = "2026-11-01") LocalDate dataFaturamento,
        @Schema(requiredMode = REQUIRED, description = "Não anterior ao faturamento.", example = "2030-12-01") LocalDate dataVencimento,
        @Schema(requiredMode = REQUIRED, minimum = "0.01", maximum = "999999999999.99", multipleOf = 0.01,
                description = "Positivo, até 12 inteiros e duas casas; sem arredondamento de fração de centavo.", example = "100.00") BigDecimal valorFatura,
        @Schema(requiredMode = REQUIRED, minimum = "0.01", maximum = "999999999999.99", multipleOf = 0.01,
                description = "Deve igualar valorFatura; não representa disponibilidade para financiamento.", example = "100.00") BigDecimal valorDuplicata,
        @Schema(requiredMode = REQUIRED) DocumentoFiscalRequest documentoFiscal) {

    public DadosEscrituracao paraDominio() {
        return new DadosEscrituracao(sacado == null ? null : sacado.paraDominio(), tipoDuplicata, numeroDuplicata,
                numeroFatura, numeroParcela, dataFaturamento, dataVencimento, valorFatura, valorDuplicata,
                documentoFiscal == null ? null : documentoFiscal.paraDominio());
    }

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record SacadoRequest(
            @Schema(requiredMode = REQUIRED, description = "CNPJ completo numérico ou alfanumérico, compacto ou máscara convencional. "
                    + "Normaliza letras ASCII para maiúsculas e valida DVs; não comprova cadastro.", example = "12.ABC.345/01DE-35") String cnpj,
            @Schema(requiredMode = REQUIRED, description = "Não vazio; até 150 caracteres após aparar espaços.", example = "Sacado fictício") String nome,
            @Schema(requiredMode = REQUIRED) EnderecoRequest domicilio) {
        DadosEscrituracao.Sacado paraDominio() {
            return new DadosEscrituracao.Sacado(cnpj, nome, domicilio == null ? null : domicilio.paraDominio());
        }
    }

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record EnderecoRequest(
            @Schema(requiredMode = REQUIRED, description = "Não vazio; até 150 caracteres após aparar espaços.", example = "Rua de Teste") String logradouro,
            @Schema(requiredMode = REQUIRED, description = "Não vazio; até 20 caracteres após aparar espaços.", example = "10") String numero,
            @Schema(requiredMode = REQUIRED, description = "Não vazio; até 100 caracteres após aparar espaços.", example = "Centro") String bairro,
            @Schema(requiredMode = REQUIRED, description = "Oito algarismos, hífen opcional, após aparar espaços.", example = "01001-000") String cep,
            @Schema(requiredMode = REQUIRED, description = "Sete algarismos após aparar espaços; sem consulta ao IBGE.", example = "3550308") String municipioIbge,
            @Schema(requiredMode = REQUIRED, description = "UF brasileira válida; normaliza para maiúsculas.", example = "SP") String uf) {
        Endereco paraDominio() {
            return new Endereco(logradouro, numero, bairro, cep, municipioIbge, uf);
        }
    }

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record DocumentoFiscalRequest(
            @Schema(requiredMode = REQUIRED, allowableValues = "NFE") String tipo,
            @Schema(requiredMode = REQUIRED, description = "Algarismos, até nove posições; normaliza zeros à esquerda.", example = "123") String numero,
            @Schema(requiredMode = REQUIRED, description = "Algarismos, até três posições; normaliza zeros à esquerda.", example = "1") String serie) {
        DadosEscrituracao.DocumentoFiscal paraDominio() {
            return new DadosEscrituracao.DocumentoFiscal(tipo, numero, serie);
        }
    }
}
