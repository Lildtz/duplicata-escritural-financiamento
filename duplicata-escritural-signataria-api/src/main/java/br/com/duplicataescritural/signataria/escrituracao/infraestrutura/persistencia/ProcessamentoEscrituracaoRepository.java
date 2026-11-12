package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ProcessamentoEscrituracaoRepository extends JpaRepository<ProcessamentoEscrituracao, UUID> {

    Optional<ProcessamentoEscrituracao> findBySacadorIdAndOperacaoAndChaveIdempotencia(
            UUID sacadorId, String operacao, String chaveIdempotencia);

    Optional<ProcessamentoEscrituracao> findByIdAndSacadorId(UUID id, UUID sacadorId);

    // JPA não expressa SKIP LOCKED: cada consumidor reserva uma linha disponível, sem esperar pelos demais.
    @Query(value = """
            SELECT * FROM escrituradora.processamento_escrituracao
            WHERE (estado = 'PENDENTE' AND proxima_execucao_em <= :agora)
               OR (estado = 'EM_PROCESSAMENTO' AND reserva_expira_em <= :agora)
            ORDER BY proxima_execucao_em, recebido_em, id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ProcessamentoEscrituracao> buscarElegivel(Instant agora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcessamentoEscrituracao p where p.id = :id")
    Optional<ProcessamentoEscrituracao> bloquear(UUID id);
}
