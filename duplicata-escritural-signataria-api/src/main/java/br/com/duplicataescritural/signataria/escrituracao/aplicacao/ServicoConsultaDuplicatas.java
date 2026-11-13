package br.com.duplicataescritural.signataria.escrituracao.aplicacao;

import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.DuplicataRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoConsultaDuplicatas {
    private final DuplicataRepository duplicatas;

    public ServicoConsultaDuplicatas(DuplicataRepository duplicatas) {
        this.duplicatas = duplicatas;
    }

    @Transactional(readOnly = true, timeout = 10)
    public Slice<DuplicataConsultada> consultar(UUID sacadorId, Cnpj cnpj, int pagina, int tamanho) {
        return duplicatas.findBySacadorIdAndSacadorCnpjOrderByEscrituradaEmAscIdAsc(
                sacadorId, cnpj, PageRequest.of(pagina, tamanho)).map(duplicata -> {
                    var dados = duplicata.dados();
                    return new DuplicataConsultada(duplicata.id(), dados.numeroDuplicata(), dados.sacado().cnpj(),
                            dados.dataVencimento(), dados.valorDuplicata(), duplicata.escrituradaEm());
                });
    }
}
