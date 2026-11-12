package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.util.UUID;
import java.util.Optional;

import br.com.duplicataescritural.signataria.escrituracao.dominio.Sacador;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SacadorRepository extends JpaRepository<Sacador, UUID> {
    Optional<Sacador> findByCnpj(Cnpj cnpj);
}
