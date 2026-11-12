package br.com.duplicataescritural.signataria.escrituracao.api;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

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
public class EscrituracaoController {

    private final ServicoEscrituracao servico;

    public EscrituracaoController(ServicoEscrituracao servico) {
        this.servico = servico;
    }

    @PostMapping("/manuais")
    public ResponseEntity<RespostaProcessamento> solicitar(@AuthenticationPrincipal SacadorAutenticado autenticado,
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
    public RespostaProcessamento consultar(@AuthenticationPrincipal SacadorAutenticado autenticado,
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
    public record RespostaProcessamento(UUID idProcessamento, ProcessamentoEscrituracao.Estado status,
                                       Instant recebidoEm, UUID idDuplicata, Instant escrituradaEm,
                                       Instant finalizadoEm, String codigo, String motivo) { }
}
