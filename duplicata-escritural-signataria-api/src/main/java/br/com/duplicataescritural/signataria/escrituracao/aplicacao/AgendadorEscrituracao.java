package br.com.duplicataescritural.signataria.escrituracao.aplicacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "signataria.escrituracao.processamento", name = "habilitado", havingValue = "true")
public class AgendadorEscrituracao {
    private static final Logger LOG = LoggerFactory.getLogger(AgendadorEscrituracao.class);
    private final ServicoProcessamentoEscrituracao servico;

    public AgendadorEscrituracao(ServicoProcessamentoEscrituracao servico) {
        this.servico = servico;
    }

    @Scheduled(fixedDelayString = "${signataria.escrituracao.processamento.polling:1s}")
    public void processar() {
        try {
            servico.processarProximo();
        } catch (RuntimeException exception) {
            // Se T1/T3 falhar, o banco preserva a pendência/reserva para o próximo polling.
            LOG.warn("Não foi possível coordenar a escrituração; trabalho durável será reavaliado no próximo polling");
        }
    }
}
