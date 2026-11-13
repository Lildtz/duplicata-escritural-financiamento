package br.com.duplicataescritural.signataria.observabilidade;

import java.util.stream.Stream;

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class ObservabilidadeTests {
    private static final String SEGREDO = "conteudo-financeiro-que-nao-pode-ser-registrado";

    @ParameterizedTest
    @MethodSource("falhas")
    void classificaSemExporCausasNemDuplicarContagem(RuntimeException falha, String resultado, String motivo,
            CapturedOutput output) {
        var metricas = new SimpleMeterRegistry();
        var registro = ObservationRegistry.create();
        registro.observationConfig().observationHandler(new DefaultMeterObservationHandler(metricas));
        registro.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override public boolean supportsContext(Observation.Context contexto) { return true; }
            @Override public void onError(Observation.Context contexto) {
                assertThat(contexto.getError().getMessage()).isEqualTo(motivo).doesNotContain(SEGREDO);
            }
        });
        var observabilidade = new Observabilidade(registro, metricas);
        assertThatThrownBy(() -> observabilidade.observar("consultar_duplicatas", () -> { throw falha; })).isSameAs(falha);
        var timer = metricas.get("signataria.operacao").tag("operacao", "consultar_duplicatas")
                .tag("resultado", resultado).tag("motivo", motivo).timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTags()).extracting(io.micrometer.core.instrument.Tag::getKey)
                .containsExactlyInAnyOrder("operacao", "resultado", "motivo", "error");
        assertThat(output.getAll()).doesNotContain(SEGREDO);
        metricas.close();
    }

    static Stream<Arguments> falhas() {
        return Stream.of(
                Arguments.of(new AccessDeniedException(SEGREDO), "rejeitado", "acesso_negado"),
                Arguments.of(new IllegalArgumentException(SEGREDO), "rejeitado", "parametros"),
                Arguments.of(new DataAccessResourceFailureException(SEGREDO), "falha", "infraestrutura"),
                Arguments.of(new IllegalStateException(SEGREDO), "falha", "interno"));
    }
}
