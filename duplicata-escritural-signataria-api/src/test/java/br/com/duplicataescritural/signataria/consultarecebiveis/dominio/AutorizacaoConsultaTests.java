package br.com.duplicataescritural.signataria.consultarecebiveis.dominio;

import java.time.Instant;
import java.util.UUID;

import br.com.duplicataescritural.signataria.escrituracao.dominio.ChaveIdempotencia;
import br.com.duplicataescritural.signataria.escrituracao.dominio.Cnpj;
import org.junit.jupiter.api.Test;

import static br.com.duplicataescritural.signataria.consultarecebiveis.dominio.AutorizacaoConsulta.*;
import static org.assertj.core.api.Assertions.*;

class AutorizacaoConsultaTests {
    private static final Instant AGORA = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant FIM = AGORA.plusSeconds(60);

    private AutorizacaoConsulta pendente() {
        return solicitar(UUID.randomUUID(), UUID.randomUUID(), new Cnpj("11222333000181"),
                new ChaveIdempotencia("chave"), FIM, AGORA);
    }

    @Test
    void aprovacaoTemVigenciaSemiabertaERepeticaoPreservaDatas() {
        var a = pendente();
        assertThat(a.vigente(AGORA)).isFalse();
        a.decidir(Decisao.APROVAR, AGORA.plusSeconds(1));
        assertThat(a.vigente(AGORA)).isFalse();
        assertThat(a.vigente(AGORA.plusSeconds(1))).isTrue();
        assertThat(a.vigente(FIM.minusNanos(1))).isTrue();
        assertThat(a.vigente(FIM)).isFalse();
        a.decidir(Decisao.APROVAR, FIM.plusSeconds(1));
        assertThat(a.estado()).isEqualTo(Estado.ATIVA);
        assertThat(a.decididaEm()).isEqualTo(AGORA.plusSeconds(1));
        assertThat(a.inicioVigencia()).isEqualTo(a.decididaEm());
        assertThatThrownBy(() -> a.decidir(Decisao.RECUSAR, AGORA.plusSeconds(2))).isInstanceOf(ConflitoAutorizacao.class);
    }

    @Test
    void recusaERepetivelMasImpedeAprovacaoERevogacao() {
        var a = pendente();
        a.decidir(Decisao.RECUSAR, AGORA);
        a.decidir(Decisao.RECUSAR, FIM);
        assertThat(a.decididaEm()).isEqualTo(AGORA);
        assertThat(a.inicioVigencia()).isNull();
        assertThat(a.vigente(AGORA)).isFalse();
        assertThatThrownBy(() -> a.decidir(Decisao.APROVAR, AGORA)).isInstanceOf(ConflitoAutorizacao.class);
        assertThatThrownBy(() -> a.revogar(RevogadoPor.SACADOR, AGORA)).isInstanceOf(ConflitoAutorizacao.class);
    }

    @Test
    void revogacaoDePendenteOuAtivaNaoPodeSerDesfeita() {
        for (boolean aprovada : new boolean[] {false, true}) {
            var a = pendente();
            if (aprovada) a.decidir(Decisao.APROVAR, AGORA);
            a.revogar(RevogadoPor.FINANCIADOR, AGORA.plusSeconds(2));
            a.revogar(RevogadoPor.SACADOR, FIM);
            assertThat(a.estado()).isEqualTo(Estado.REVOGADA);
            assertThat(a.revogadaEm()).isEqualTo(AGORA.plusSeconds(2));
            assertThat(a.revogadoPor()).isEqualTo(RevogadoPor.FINANCIADOR);
            assertThat(a.vigente(AGORA.plusSeconds(3))).isFalse();
            assertThatThrownBy(() -> a.decidir(Decisao.APROVAR, AGORA.plusSeconds(3))).isInstanceOf(ConflitoAutorizacao.class);
            assertThatThrownBy(() -> a.decidir(Decisao.RECUSAR, AGORA.plusSeconds(3))).isInstanceOf(ConflitoAutorizacao.class);
        }
    }

    @Test
    void pendenteExpiradaRecusaNovaDecisaoMasPermiteRevogacao() {
        var a = pendente();
        assertThatThrownBy(() -> a.decidir(Decisao.APROVAR, FIM)).isInstanceOf(ConflitoAutorizacao.class)
                .extracting("codigo").isEqualTo("AUTORIZACAO_EXPIRADA");
        assertThatThrownBy(() -> a.decidir(Decisao.RECUSAR, FIM)).isInstanceOf(ConflitoAutorizacao.class);
        assertThat(a.estado()).isEqualTo(Estado.PENDENTE);
        a.revogar(RevogadoPor.SACADOR, FIM);
        assertThat(a.estado()).isEqualTo(Estado.REVOGADA);
    }

    @Test
    void normalizaConteudoParaComparacaoSemPermitirTrocaDeEscopo() {
        var a = pendente();
        a.conferirRepeticao(new Cnpj(" 11.222.333/0001-81 "), FIM.plusNanos(123));
        assertThatThrownBy(() -> a.conferirRepeticao(new Cnpj("12ABC34501DE35"), FIM)).isInstanceOf(ConflitoAutorizacao.class);
        assertThatThrownBy(() -> a.conferirRepeticao(a.cnpjSacador(), FIM.plusSeconds(1))).isInstanceOf(ConflitoAutorizacao.class);
        assertThatIllegalArgumentException().isThrownBy(() -> solicitar(UUID.randomUUID(), UUID.randomUUID(),
                a.cnpjSacador(), new ChaveIdempotencia("outra"), AGORA, AGORA));
        assertThatIllegalArgumentException().isThrownBy(() -> normalizarInstante(null));
        assertThatThrownBy(() -> a.decidir(Decisao.APROVAR, AGORA.minusSeconds(1))).isInstanceOf(ConflitoAutorizacao.class);
    }
}
