package br.com.duplicataescritural.signataria.consultarecebiveis.infraestrutura.persistencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.duplicataescritural.signataria.consultarecebiveis.dominio.AutorizacaoConsulta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AutorizacaoConsultaRepository extends JpaRepository<AutorizacaoConsulta, UUID> {
    Optional<AutorizacaoConsulta> findByFinanciadorIdAndOperacaoAndChaveIdempotencia(UUID financiadorId, String operacao, String chave);

    Optional<AutorizacaoConsulta> findByIdAndFinanciadorId(UUID id, UUID financiadorId);
    Optional<AutorizacaoConsulta> findByIdAndSacadorId(UUID id, UUID sacadorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AutorizacaoConsulta a where a.id = :id and a.financiadorId = :financiadorId")
    Optional<AutorizacaoConsulta> bloquearDoFinanciador(UUID id, UUID financiadorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AutorizacaoConsulta a where a.id = :id and a.sacadorId = :sacadorId")
    Optional<AutorizacaoConsulta> bloquearDoSacador(UUID id, UUID sacadorId);

    // ON CONFLICT evita abortar a transação PostgreSQL na disputa pela mesma chave.
    @Modifying
    @Query(value = """
            INSERT INTO escrituradora.autorizacao_consulta
                (id, financiador_id, sacador_id, cnpj_sacador, operacao, chave_idempotencia,
                 solicitada_em, fim_vigencia, estado)
            VALUES (:id, :financiadorId, :sacadorId, :cnpj, 'SOLICITAR_AUTORIZACAO_CONSULTA', :chave,
                    :agora, :fim, 'PENDENTE')
            ON CONFLICT ON CONSTRAINT uk_autorizacao_consulta_idempotencia DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(UUID id, UUID financiadorId, UUID sacadorId, String cnpj, String chave, Instant agora, Instant fim);
}
