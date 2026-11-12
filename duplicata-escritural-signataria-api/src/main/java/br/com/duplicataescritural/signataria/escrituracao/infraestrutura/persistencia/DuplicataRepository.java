package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.Duplicata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DuplicataRepository extends JpaRepository<Duplicata, UUID> { }
