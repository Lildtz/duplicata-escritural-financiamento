package br.com.duplicataescritural.signataria.escrituracao.api;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import br.com.duplicataescritural.signataria.escrituracao.aplicacao.ServicoEscrituracao;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao;
import br.com.duplicataescritural.signataria.seguranca.SacadorAutenticado;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/escrituracoes")
@SecurityRequirement(name = "BearerLocal")
@ApiResponses({
        @ApiResponse(responseCode = "401", ref = "#/components/responses/NaoAutenticado"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/PapelNegado"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/FalhaEscrituracao"),
        @ApiResponse(responseCode = "503", ref = "#/components/responses/IndisponibilidadeEscrituracao")})
public class EscrituracaoController {

    private final ServicoEscrituracao servico;

    public EscrituracaoController(ServicoEscrituracao servico) {
        this.servico = servico;
    }

    @PostMapping("/manuais")
    @Operation(summary = "Receber Escrituração Manual", tags = "Escrituração Manual",
            description = "SACADOR_LOCAL. Identidade vem do token/cadastro. Recebimento durável não confirma Escrituração. "
                    + "Mesma chave e conteúdo normalizado preservam ID, instante, PENDENTE e Location, inclusive após processamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", ref = "#/components/responses/RequisicaoEscrituracao"),
            @ApiResponse(responseCode = "202", description = "Recebimento confirmado após commit.",
                    headers = @Header(name = "Location", description = "GET do processamento.", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RespostaProcessamento.class))),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflitoEscrituracao"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/DadosEscrituracaoInvalidos")})
    public ResponseEntity<RespostaProcessamento> solicitar(@Parameter(hidden = true) @AuthenticationPrincipal SacadorAutenticado autenticado,
            @Parameter(required = true, description = "Gerada pelo chamador; escopo Sacador/operação; sem expiração automática. "
                    + "Reenvio deve manter chave e conteúdo.", schema = @Schema(minLength = 1, maxLength = 128, pattern = "[A-Za-z0-9._:-]+"))
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @RequestBody SolicitacaoEscrituracaoRequest solicitacao) {
        var dados = solicitacao.paraDominio();
        ChaveIdempotencia chaveValidada;
        try {
            chaveValidada = new ChaveIdempotencia(chave);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        var processamento = servico.receber(autenticado.sacador(), chaveValidada, dados);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/escrituracoes/processamentos/" + processamento.id()))
                .body(new RespostaProcessamento(processamento.id(), ProcessamentoEscrituracao.Estado.PENDENTE,
                        processamento.recebidoEm(), null, null, null, null, null));
    }

    @GetMapping("/processamentos/{idProcessamento}")
    @Operation(summary = "Consultar processamento", tags = "Processamentos",
            description = "SACADOR_LOCAL correspondente. Estado atual: PENDENTE, EM_PROCESSAMENTO, CONCLUIDO, REJEITADO ou FALHA_TECNICA. "
                    + "CONCLUIDO significa somente criação da Duplicata local; campos sem aplicação são omitidos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atual do processamento.", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RespostaProcessamento.class))),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/DadosEscrituracaoInvalidos"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProcessamentoNaoEncontrado")})
    public RespostaProcessamento consultar(@Parameter(hidden = true) @AuthenticationPrincipal SacadorAutenticado autenticado,
                                          @PathVariable UUID idProcessamento) {
        return resposta(servico.consultar(idProcessamento, autenticado.sacador().id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processamento não encontrado")));
    }

    private RespostaProcessamento resposta(ProcessamentoEscrituracao processamento) {
        boolean falhaTerminal = processamento.estado() == ProcessamentoEscrituracao.Estado.REJEITADO
                || processamento.estado() == ProcessamentoEscrituracao.Estado.FALHA_TECNICA;
        var codigo = falhaTerminal ? processamento.codigoResultado() : null;
        return new RespostaProcessamento(processamento.id(), processamento.estado(), processamento.recebidoEm(),
                processamento.idDuplicata(), processamento.escrituradaEm(), processamento.finalizadoEm(),
                codigo == null ? null : codigo.name(), codigo == null ? null : codigo.motivo());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RespostaProcessamento(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID idProcessamento,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProcessamentoEscrituracao.Estado status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recebidoEm,
            @Schema(description = "Somente CONCLUIDO; UUID técnico, não é IUD.") UUID idDuplicata,
            @Schema(description = "Somente CONCLUIDO.") Instant escrituradaEm,
            @Schema(description = "Somente estados terminais.") Instant finalizadoEm,
            @Schema(description = "Código seguro somente em REJEITADO/FALHA_TECNICA.") String codigo,
            @Schema(description = "Motivo seguro somente em REJEITADO/FALHA_TECNICA.") String motivo) { }
}
