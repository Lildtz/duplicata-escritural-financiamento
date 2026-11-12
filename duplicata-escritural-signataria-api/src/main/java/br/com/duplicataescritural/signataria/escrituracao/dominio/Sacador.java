package br.com.duplicataescritural.signataria.escrituracao.dominio;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sacador", schema = "escrituradora")
public class Sacador {

    @Id
    private UUID id;

    @Column(nullable = false, length = 14, unique = true)
    private Cnpj cnpj;

    @Column(nullable = false, length = 150)
    private String nome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Endereco domicilio;

    @Column(name = "inicio_operacao", nullable = false)
    private LocalDate inicioOperacao;

    @Column(nullable = false)
    private boolean habilitado;

    protected Sacador() { }

    public Sacador(UUID id, Cnpj cnpj, String nome, Endereco domicilio, LocalDate inicioOperacao, boolean habilitado) {
        this.id = Objects.requireNonNull(id);
        this.cnpj = Objects.requireNonNull(cnpj);
        this.nome = Endereco.texto(nome, "Nome do Sacador", 150);
        this.domicilio = Objects.requireNonNull(domicilio);
        this.inicioOperacao = Objects.requireNonNull(inicioOperacao);
        this.habilitado = habilitado;
    }

    public UUID id() { return id; }
    public Cnpj cnpj() { return cnpj; }
    public String nome() { return nome; }
    public Endereco domicilio() { return domicilio; }
    public LocalDate inicioOperacao() { return inicioOperacao; }
    public boolean habilitado() { return habilitado; }

    public FotografiaSacador fotografia() {
        return new FotografiaSacador(id, cnpj, nome, domicilio, inicioOperacao, habilitado);
    }
}
