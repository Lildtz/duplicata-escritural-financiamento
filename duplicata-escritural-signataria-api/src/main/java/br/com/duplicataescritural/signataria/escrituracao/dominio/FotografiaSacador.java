package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.time.LocalDate;
import java.util.UUID;

public record FotografiaSacador(UUID id, Cnpj cnpj, String nome, Endereco domicilio,
                               LocalDate inicioOperacao, boolean habilitado) { }
