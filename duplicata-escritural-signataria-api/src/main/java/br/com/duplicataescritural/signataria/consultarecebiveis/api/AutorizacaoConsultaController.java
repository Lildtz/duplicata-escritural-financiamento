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
public class AutorizacaoConsultaController {
    private final ServicoAutorizacaoConsulta servico;
    private final Clock relogio;

    public AutorizacaoConsultaController(ServicoAutorizacaoConsulta servico, Clock relogio) {
        this.servico = servico;
        this.relogio = relogio;
    }

    @PostMapping
    public ResponseEntity<RespostaAutorizacao> solicitar(@AuthenticationPrincipal FinanciadorAutenticado financiador,
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
    public RespostaAutorizacao consultar(@AuthenticationPrincipal ParticipanteAutenticado participante, @PathVariable UUID id) {
        return resposta(servico.consultar(participante, id));
    }

    @GetMapping("/{id}/duplicatas")
    public RespostaDuplicatas consultarDuplicatas(@AuthenticationPrincipal ParticipanteAutenticado participante,
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho, @RequestParam MultiValueMap<String, String> parametros) {
        if (!Set.of("pagina", "tamanho").containsAll(parametros.keySet())
                || parametros.values().stream().anyMatch(valores -> valores.size() != 1 || valores.getFirst().isBlank())) {
            throw new ParametrosConsultaInvalidos();
        }
        var resultado = servico.consultarDuplicatas(participante, id, pagina, tamanho);
        var duplicatas = resultado.duplicatas();
        return new RespostaDuplicatas(resultado.cnpjSacador(), "LOCAL_SIMULADO", duplicatas.getContent(),
                duplicatas.getNumber(), duplicatas.getSize(), duplicatas.hasNext());
    }

    public record RespostaDuplicatas(String cnpjSacador, String escopo, List<DuplicataConsultada> itens,
            int pagina, int tamanho, boolean temProximaPagina) { }

    @PutMapping("/{id}/decisao")
    public RespostaAutorizacao decidir(@AuthenticationPrincipal SacadorAutenticado sacador, @PathVariable UUID id,
            @Valid @RequestBody DecisaoAutorizacao decisao) {
        return resposta(servico.decidir(sacador, id, decisao.decisao()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revogar(@AuthenticationPrincipal ParticipanteAutenticado participante, @PathVariable UUID id) {
        servico.revogar(participante, id);
        return ResponseEntity.noContent().build();
    }

    private RespostaAutorizacao resposta(AutorizacaoConsulta autorizacao) {
        return new RespostaAutorizacao(autorizacao.id(), autorizacao.financiadorId(), autorizacao.cnpjSacador().valor(),
                autorizacao.estado(), autorizacao.vigente(relogio.instant()), autorizacao.solicitadaEm(),
                autorizacao.inicioVigencia(), autorizacao.fimVigencia(), autorizacao.decididaEm(),
                autorizacao.revogadaEm(), autorizacao.revogadoPor());
    }

    public record SolicitacaoAutorizacao(@NotNull String cnpjSacador, @NotNull Instant fimVigencia) { }
    public record DecisaoAutorizacao(@NotNull AutorizacaoConsulta.Decisao decisao) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RespostaAutorizacao(UUID idAutorizacao, UUID financiadorId, String cnpjSacador,
            AutorizacaoConsulta.Estado status, boolean vigente, Instant solicitadaEm, Instant inicioVigencia,
            Instant fimVigencia, Instant decididaEm, Instant revogadaEm, AutorizacaoConsulta.RevogadoPor revogadoPor) { }
}
