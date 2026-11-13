package br.com.duplicataescritural.signataria.consultarecebiveis.api;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Set;

import br.com.duplicataescritural.signataria.consultarecebiveis.aplicacao.ParametrosConsultaInvalidos;
import br.com.duplicataescritural.signataria.consultarecebiveis.aplicacao.ServicoAutorizacaoConsulta;
import br.com.duplicataescritural.signataria.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import br.com.duplicataescritural.signataria.escrituracao.aplicacao.DuplicataConsultada;
import br.com.duplicataescritural.signataria.seguranca.FinanciadorAutenticado;
import br.com.duplicataescritural.signataria.seguranca.ParticipanteAutenticado;
import br.com.duplicataescritural.signataria.seguranca.SacadorAutenticado;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/autorizacoes-consulta")
@SecurityRequirement(name = "BearerLocal")
@ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/RequisicaoAutorizacao"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/NaoAutenticado"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/AcessoAutorizacaoNegado"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/AutorizacaoNaoEncontrada"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/FalhaAutorizacao"),
        @ApiResponse(responseCode = "503", ref = "#/components/responses/IndisponibilidadeAutorizacao")})
public class AutorizacaoConsultaController {
    private final ServicoAutorizacaoConsulta servico;
    private final Clock relogio;

    public AutorizacaoConsultaController(ServicoAutorizacaoConsulta servico, Clock relogio) {
        this.servico = servico;
        this.relogio = relogio;
    }

    @PostMapping
    @Operation(summary = "Solicitar autorização", tags = "Autorizações de consulta",
            description = "FINANCIADOR_LOCAL. Beneficiário vem exclusivamente do Bearer. CNPJ completo de Sacador local habilitado. "
                    + "Escopo local simulado. Repetição válida mantém HTTP 201, Location e representação original PENDENTE/vigente=false, "
                    + "inclusive após decisão, revogação ou expiração. GET informa estado atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido original ou repetição idempotente válida.",
                    headers = @Header(name = "Location", description = "GET da autorização.", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RespostaAutorizacao.class))),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflitoAutorizacao"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/DadosAutorizacaoInvalidos")})
    public ResponseEntity<RespostaAutorizacao> solicitar(@Parameter(hidden = true) @AuthenticationPrincipal FinanciadorAutenticado financiador,
            @Parameter(required = true, description = "Gerada pelo chamador; escopo Financiador/operação, sem expiração automática. "
                    + "Repetição exige mesmo CNPJ canônico e fim da vigência normalizado a microssegundos.",
                    schema = @Schema(minLength = 1, maxLength = 128, pattern = "[A-Za-z0-9._:-]+"))
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @Valid @RequestBody SolicitacaoAutorizacao solicitacao) {
        ChaveIdempotencia chaveValidada;
        try {
            chaveValidada = new ChaveIdempotencia(chave);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key inválida ou ausente");
        }
        var autorizacao = servico.solicitar(financiador, chaveValidada, new Cnpj(solicitacao.cnpjSacador()), solicitacao.fimVigencia());
        // Repetições do POST preservam o recebimento original, inclusive após decisão ou expiração.
        var recebimento = new RespostaAutorizacao(autorizacao.id(), autorizacao.financiadorId(),
                autorizacao.cnpjSacador().valor(), AutorizacaoConsulta.Estado.PENDENTE, false,
                autorizacao.solicitadaEm(), null, autorizacao.fimVigencia(), null, null, null);
        return ResponseEntity.created(URI.create("/api/v1/autorizacoes-consulta/" + autorizacao.id())).body(recebimento);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar autorização", tags = "Autorizações de consulta",
            description = "SACADOR_LOCAL correspondente ou FINANCIADOR_LOCAL beneficiário. ATIVA não significa necessariamente vigente; "
                    + "o campo vigente considera início <= agora < fim. Expiração não muda o estado persistido.")
    @ApiResponse(responseCode = "200", description = "Estado e vigência atuais.", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = RespostaAutorizacao.class)))
    public RespostaAutorizacao consultar(@Parameter(hidden = true) @AuthenticationPrincipal ParticipanteAutenticado participante, @PathVariable UUID id) {
        return resposta(servico.consultar(participante, id));
    }

    @GetMapping("/{id}/duplicatas")
    @Operation(summary = "Consultar Duplicatas locais autorizadas", tags = "Consulta de Duplicatas",
            description = "Somente FINANCIADOR_LOCAL beneficiário de autorização ATIVA e vigente. Revalidação em cada requisição/página; "
                    + "revogação confirmada bloqueia novas consultas. Isolamento por Sacador e CNPJ completo. "
                    + "Ordem estável escrituradaEm/ID técnico, sem sort do cliente ou parâmetros extras/repetidos. "
                    + "Sem snapshot entre páginas, Agenda regulatória completa ou cálculo de saldo financiável.")
    @ApiResponse(responseCode = "200", description = "Página local; vazia é válida somente quando autorizada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RespostaDuplicatas.class)))
    public RespostaDuplicatas consultarDuplicatas(@Parameter(hidden = true) @AuthenticationPrincipal ParticipanteAutenticado participante,
            @PathVariable UUID id,
            @Parameter(description = "Índice da página, iniciando em zero.", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Quantidade máxima de itens na página.", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "50"))
            @RequestParam(defaultValue = "50") int tamanho,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parametros) {
        if (!Set.of("pagina", "tamanho").containsAll(parametros.keySet())
                || parametros.values().stream().anyMatch(valores -> valores.size() != 1 || valores.getFirst().isBlank())) {
            throw new ParametrosConsultaInvalidos();
        }
        var resultado = servico.consultarDuplicatas(participante, id, pagina, tamanho);
        var duplicatas = resultado.duplicatas();
        return new RespostaDuplicatas(resultado.cnpjSacador(), "LOCAL_SIMULADO", duplicatas.getContent(),
                duplicatas.getNumber(), duplicatas.getSize(), duplicatas.hasNext());
    }

    public record RespostaDuplicatas(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "[A-Z0-9]{12}[0-9]{2}") String cnpjSacador,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "LOCAL_SIMULADO") String escopo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DuplicataConsultada> itens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int pagina,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "100") int tamanho,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean temProximaPagina) { }

    @PutMapping("/{id}/decisao")
    @Operation(summary = "Decidir autorização", tags = "Autorizações de consulta",
            description = "Somente SACADOR_LOCAL correspondente: APROVAR ou RECUSAR. PENDENTE vigente torna-se ATIVA ou RECUSADA. "
                    + "Repetir a mesma decisão preserva datas; não renova vigência. Pedido expirado ou transição incompatível retorna 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atual após decisão.", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RespostaAutorizacao.class))),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflitoAutorizacao"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/DadosAutorizacaoInvalidos")})
    public RespostaAutorizacao decidir(@Parameter(hidden = true) @AuthenticationPrincipal SacadorAutenticado sacador, @PathVariable UUID id,
            @Valid @RequestBody DecisaoAutorizacao decisao) {
        return resposta(servico.decidir(sacador, id, decisao.decisao()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revogar autorização", tags = "Autorizações de consulta",
            description = "SACADOR_LOCAL correspondente ou FINANCIADOR_LOCAL beneficiário. Registra REVOGADA, sem excluir o pedido. "
                    + "Repetições preservam primeiro autor e instante; RECUSADA não permite revogação. Novas consultas são bloqueadas após commit.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Revogação confirmada, sem corpo.", content = @Content),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflitoAutorizacao")})
    public ResponseEntity<Void> revogar(@Parameter(hidden = true) @AuthenticationPrincipal ParticipanteAutenticado participante, @PathVariable UUID id) {
        servico.revogar(participante, id);
        return ResponseEntity.noContent().build();
    }

    private RespostaAutorizacao resposta(AutorizacaoConsulta autorizacao) {
        return new RespostaAutorizacao(autorizacao.id(), autorizacao.financiadorId(), autorizacao.cnpjSacador().valor(),
                autorizacao.estado(), autorizacao.vigente(relogio.instant()), autorizacao.solicitadaEm(),
                autorizacao.inicioVigencia(), autorizacao.fimVigencia(), autorizacao.decididaEm(),
                autorizacao.revogadaEm(), autorizacao.revogadoPor());
    }

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record SolicitacaoAutorizacao(
            @Schema(description = "CNPJ completo numérico ou alfanumérico, compacto ou com máscara convencional; DVs validados.", example = "12ABC34501DE35")
            @NotNull String cnpjSacador,
            @Schema(description = "Futuro para novo pedido; normalizado à precisão de microssegundos. Exemplo deve ser ajustado à execução.", example = "2030-12-31T23:59:59Z")
            @NotNull Instant fimVigencia) { }
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record DecisaoAutorizacao(@NotNull AutorizacaoConsulta.Decisao decisao) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RespostaAutorizacao(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID idAutorizacao,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Beneficiário definido pelo token do pedido.") UUID financiadorId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "[A-Z0-9]{12}[0-9]{2}") String cnpjSacador,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AutorizacaoConsulta.Estado status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ATIVA e início <= agora < fim; expiração não altera o estado.") boolean vigente,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant solicitadaEm,
            @Schema(description = "Omitido antes da aprovação.") Instant inicioVigencia,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant fimVigencia,
            @Schema(description = "Omitido antes da decisão.") Instant decididaEm,
            @Schema(description = "Omitido antes da revogação.") Instant revogadaEm,
            @Schema(description = "Omitido antes da revogação.") AutorizacaoConsulta.RevogadoPor revogadoPor) { }
}
