package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.util.Optional;
import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ProcessamentoEscrituracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ProcessamentosEscrituracao extends JpaRepository<ProcessamentoEscrituracao, UUID> {

    Optional<ProcessamentoEscrituracao> findBySacadorIdAndOperacaoAndChaveIdempotencia(
            UUID sacadorId, String operacao, String chaveIdempotencia);

    Optional<ProcessamentoEscrituracao> findByIdAndSacadorId(UUID id, UUID sacadorId);
}
