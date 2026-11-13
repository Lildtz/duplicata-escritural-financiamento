package br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.AutorizacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.FalhaRegistradora;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.GatewayRegistradora;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginaDuplicatas;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.PaginacaoConsulta;
import br.com.duplicataescritural.financiamento.consultarecebiveis.dominio.SolicitacaoAutorizacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ServicoConsultaRecebiveisTests {
    private static final Instant AGORA = Instant.parse("2030-01-01T00:00:00Z");
    private final UUID id = UUID.randomUUID();
    private final GatewayRegistradora gateway = mock(GatewayRegistradora.class);
    private final ServicoConsultaRecebiveis servico = new ServicoConsultaRecebiveis(gateway, Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    void solicitaSemAlterarChaveOuImpedirRepeticaoIdempotenteAposFimDaVigencia() {
        var pedido = new SolicitacaoAutorizacao("12ABC34501DE35", AGORA.minusSeconds(1), "Chave-1");
        var autorizacao = autorizacao();
        when(gateway.solicitarAutorizacao(pedido)).thenReturn(autorizacao);
        assertThat(servico.solicitarAutorizacao(" 12abc34501de35 ", pedido.fimVigencia(), "Chave-1")).isSameAs(autorizacao);
        verify(gateway).solicitarAutorizacao(pedido);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void consultaEstadoSemRecalcularVigencia() {
        var autorizacao = autorizacao();
        when(gateway.consultarAutorizacao(id)).thenReturn(autorizacao);
        assertThat(servico.consultarAutorizacao(id)).isSameAs(autorizacao);
        verify(gateway).consultarAutorizacao(id);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void consultaUmaPaginaComReferenciaLocalSemPreConsultarOuGuardarAutorizacao() {
        var pagina = new PaginaDuplicatas("11222333000181", PaginaDuplicatas.Escopo.LOCAL_SIMULADO, List.of(), 0, 50, false);
        when(gateway.consultarDuplicatas(id, new PaginacaoConsulta(0, 50))).thenReturn(pagina);
        var consulta = servico.consultarDuplicatas(id);
        assertThat(consulta.consultadaEm()).isEqualTo(AGORA);
        assertThat(consulta.resultado()).isSameAs(pagina);
        verify(gateway).consultarDuplicatas(id, new PaginacaoConsulta(0, 50));
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void cadaPaginaAcionaNovamenteGatewayEPreservaFalhaAposRevogacao() {
        var segunda = new PaginaDuplicatas("11222333000181", PaginaDuplicatas.Escopo.LOCAL_SIMULADO, List.of(), 1, 10, false);
        when(gateway.consultarDuplicatas(id, new PaginacaoConsulta(1, 10))).thenReturn(segunda);
        assertThat(servico.consultarDuplicatas(id, 1, 10).resultado()).isSameAs(segunda);
        var negada = new FalhaRegistradora(FalhaRegistradora.Motivo.ACESSO_NEGADO);
        when(gateway.consultarDuplicatas(id, new PaginacaoConsulta(2, 10))).thenThrow(negada);
        assertThatThrownBy(() -> servico.consultarDuplicatas(id, 2, 10)).isSameAs(negada);
        verify(gateway).consultarDuplicatas(id, new PaginacaoConsulta(1, 10));
        verify(gateway).consultarDuplicatas(id, new PaginacaoConsulta(2, 10));
        verifyNoMoreInteractions(gateway);
    }

    @ParameterizedTest
    @CsvSource({"-1,50", "0,0", "0,-1", "0,101", "2147483647,100"})
    void rejeitaPaginacaoAntesDeAcionarRede(int pagina, int tamanho) {
        assertThatThrownBy(() -> servico.consultarDuplicatas(id, pagina, tamanho)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"inválido", "11222333", "11222333000181&escopo=GLOBAL"})
    void rejeitaCnpjInvalidoAntesDeAcionarRede(String cnpj) {
        assertThatThrownBy(() -> servico.solicitarAutorizacao(cnpj, AGORA, "chave")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"chave com espaço", "chave\r\nHeader:outro"})
    void rejeitaChaveInvalidaAntesDeAcionarRede(String chave) {
        assertThatThrownBy(() -> servico.solicitarAutorizacao("11222333000181", AGORA, chave)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void rejeitaIdentificadoresEInstantesAusentes() {
        assertThatThrownBy(() -> servico.consultarAutorizacao(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> servico.consultarDuplicatas(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> servico.solicitarAutorizacao("11222333000181", null, "chave")).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @EnumSource(FalhaRegistradora.Motivo.class)
    void propagaFalhasSemTransformarEmListaVaziaOuExecutarRetry(FalhaRegistradora.Motivo motivo) {
        var falha = new FalhaRegistradora(motivo);
        when(gateway.consultarDuplicatas(id, new PaginacaoConsulta(0, 50))).thenThrow(falha);
        assertThatThrownBy(() -> servico.consultarDuplicatas(id)).isSameAs(falha);
        verify(gateway).consultarDuplicatas(id, new PaginacaoConsulta(0, 50));
        verifyNoMoreInteractions(gateway);
    }

    private AutorizacaoConsulta autorizacao() {
        return new AutorizacaoConsulta(id, UUID.randomUUID(), "11222333000181", AutorizacaoConsulta.Estado.ATIVA,
                false, AGORA.minusSeconds(60), AGORA.minusSeconds(60), AGORA.minusSeconds(1), AGORA.minusSeconds(60), null, null);
    }
}
