package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.Sacador;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SacadorRepository extends JpaRepository<Sacador, UUID> { }
