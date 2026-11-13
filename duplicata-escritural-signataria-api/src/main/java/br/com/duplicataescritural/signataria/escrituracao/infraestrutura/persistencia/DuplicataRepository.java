package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Duplicata;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DuplicataRepository extends JpaRepository<Duplicata, UUID> {
    Slice<Duplicata> findBySacadorIdAndSacadorCnpjOrderByEscrituradaEmAscIdAsc(UUID sacadorId, Cnpj sacadorCnpj, Pageable pagina);
}
