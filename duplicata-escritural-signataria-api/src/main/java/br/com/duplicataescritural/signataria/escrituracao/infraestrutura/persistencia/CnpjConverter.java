package br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia;

import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CnpjConverter implements AttributeConverter<Cnpj, String> {

    @Override
    public String convertToDatabaseColumn(Cnpj cnpj) {
        return cnpj == null ? null : cnpj.valor();
    }

    @Override
    public Cnpj convertToEntityAttribute(String valor) {
        return valor == null ? null : new Cnpj(valor);
    }
}
