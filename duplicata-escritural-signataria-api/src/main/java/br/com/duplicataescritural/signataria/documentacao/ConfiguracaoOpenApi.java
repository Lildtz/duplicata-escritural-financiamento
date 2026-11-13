package br.com.duplicataescritural.signataria.documentacao;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class ConfiguracaoOpenApi {
    @Bean
    OpenAPI contratoSignataria() {
        Schema<?> erroSeguranca = new ObjectSchema().additionalProperties(false)
                .addProperty("status", new IntegerSchema()).addProperty("title", new StringSchema());
        erroSeguranca.setRequired(List.of("status", "title"));
        var componentes = new Components()
                .addSecuritySchemes("BearerLocal", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                        .description("Token opaco provisionado exclusivamente no perfil local. Cada token identifica um Sacador "
                                + "ou Financiador; não é JWT, OAuth2 ou identidade por header arbitrário. "
                                + "Authorize não substitui as regras de papel, beneficiário, estado e vigência."))
                .addSchemas("ErroSeguranca", erroSeguranca)
                .addSchemas("ProblemaEscrituracao", problema(false))
                .addSchemas("ProblemaAutorizacao", problema(true));
        componentes.addResponses("NaoAutenticado", resposta("Credencial ausente ou inválida.", ref("ErroSeguranca"))
                .addHeaderObject("WWW-Authenticate", new io.swagger.v3.oas.models.headers.Header()
                        .schema(new StringSchema().example("Bearer"))));
        componentes.addResponses("PapelNegado", resposta("Papel incorreto ou Sacador desabilitado.", ref("ErroSeguranca")));
        componentes.addResponses("RequisicaoEscrituracao", resposta("JSON/campo/chave inválido ou ausente.", ref("ProblemaEscrituracao")));
        componentes.addResponses("DadosEscrituracaoInvalidos", resposta("Validação da emissão, de suas partes ou UUID de processamento malformado.", ref("ProblemaEscrituracao")));
        componentes.addResponses("ConflitoEscrituracao", resposta("Mesma chave com conteúdo normalizado diferente.", ref("ProblemaEscrituracao")));
        componentes.addResponses("ProcessamentoNaoEncontrado", resposta("Processamento inexistente ou de outro Sacador.", ref("ProblemaEscrituracao")));
        componentes.addResponses("RequisicaoAutorizacao", resposta("JSON, UUID, chave ou parâmetros de paginação inválidos; "
                + "códigos REQUISICAO_INVALIDA, CHAVE_IDEMPOTENCIA_INVALIDA ou PARAMETROS_CONSULTA_INVALIDOS.", ref("ProblemaAutorizacao")));
        componentes.addResponses("DadosAutorizacaoInvalidos", resposta("DADOS_INVALIDOS: CNPJ, vigência ou campos obrigatórios inválidos.", ref("ProblemaAutorizacao")));
        componentes.addResponses("AutorizacaoNaoEncontrada", resposta("RECURSO_NAO_ENCONTRADO: inexistente ou alheio; "
                + "404 genérico protege contra enumeração de participantes.", ref("ProblemaAutorizacao")));
        componentes.addResponses("ConflitoAutorizacao", resposta("Conflito idempotente, transição incompatível ou decisão de pedido expirado.", ref("ProblemaAutorizacao")));
        componentes.addResponses("AcessoAutorizacaoNegado", resposta("Papel incorreto/Sacador desabilitado (erro de segurança) "
                + "ou CONSULTA_NAO_PERMITIDA para autorização própria sem estado/vigência válidos.", alternativa("ProblemaAutorizacao")));
        for (String capacidade : List.of("Escrituracao", "Autorizacao")) {
            componentes.addResponses("Falha" + capacidade, resposta("Falha não transitória, sem detalhes internos.", alternativa("Problema" + capacidade)));
            componentes.addResponses("Indisponibilidade" + capacidade, resposta("Falha transitória ou recuperável; não comprova "
                    + "que nada foi confirmado. Não há retry automático; reenvio deve preservar chave e conteúdo.", alternativa("Problema" + capacidade)));
        }
        return new OpenAPI().components(componentes).info(new Info().title("Signatária — Duplicata Escritural").version("v1")
                .description("API experimental do simulador. Escrituração e consulta de Duplicatas locais, sem equivalência "
                        + "com Agenda regulatória completa, Registro, IUD, interoperabilidade ou disponibilidade financeira. "
                        + "Não representa implementação oficial ou vínculo institucional com infraestruturas de mercado."));
    }

    @Bean
    OpenApiCustomizer projecaoDeDuplicata() {
        // A projeção da aplicação não depende de Swagger; apenas seu contrato HTTP é enriquecido aqui.
        return api -> {
            Schema<?> titulo = api.getComponents().getSchemas().get("DuplicataConsultada");
            titulo.setRequired(List.of("idDuplicata", "numeroDuplicata", "cnpjSacado", "dataVencimento", "valorNominal", "escrituradaEm"));
            Schema<?> valor = titulo.getProperties().get("valorNominal");
            valor.setDescription("Valor imutável da emissão; não é saldo financiável.");
            valor.setMinimum(new BigDecimal("0.01"));
            valor.setMaximum(new BigDecimal("999999999999.99"));
            valor.setMultipleOf(new BigDecimal("0.01"));
            Schema<?> cnpj = titulo.getProperties().get("cnpjSacado");
            cnpj.setDescription("CNPJ completo canônico, numérico ou alfanumérico.");
            cnpj.setPattern("[A-Z0-9]{12}[0-9]{2}");
            cnpj.setExample("12ABC34501DE35");
        };
    }

    private static Schema<?> problema(boolean codigo) {
        Schema<?> schema = new ObjectSchema().addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema()).addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema()).addProperty("instance", new StringSchema().format("uri-reference"));
        if (codigo) schema.addProperty("codigo", new StringSchema().description("Código seguro retornado pelo handler de autorizações."));
        return schema.required(codigo ? List.of("title", "status", "detail", "codigo") : List.of("title", "status", "detail"));
    }

    private static Schema<?> ref(String nome) {
        return new Schema<>().$ref("#/components/schemas/" + nome);
    }

    private static Schema<?> alternativa(String problema) {
        return new Schema<>().oneOf(List.of(ref("ErroSeguranca"), ref(problema)));
    }

    private static ApiResponse resposta(String descricao, Schema<?> schema) {
        return new ApiResponse().description(descricao).content(new Content()
                .addMediaType("application/problem+json", new MediaType().schema(schema)));
    }
}
