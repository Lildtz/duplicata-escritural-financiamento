package br.com.duplicataescritural.financiamento.consultarecebiveis.infraestrutura.integracao.signataria;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("financiamento.signataria")
public record ConexaoSignataria(URI urlBase, String token, Duration timeoutConexao, Duration timeoutLeitura) {
    public ConexaoSignataria {
        if (urlBase == null || urlBase.getHost() == null || urlBase.getRawUserInfo() != null
                || urlBase.getPort() == 0 || urlBase.getPort() > 65535
                || urlBase.getRawQuery() != null || urlBase.getRawFragment() != null
                || !(urlBase.getRawPath().isEmpty() || urlBase.getRawPath().equals("/"))
                || !("https".equals(urlBase.getScheme()) || ("http".equals(urlBase.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(urlBase.getHost())))) {
            throw new IllegalArgumentException("URL base exige HTTPS ou HTTP em loopback, sem caminho ou credenciais");
        }
        if (token == null || !token.matches("[A-Za-z0-9_-]{43,128}")) {
            throw new IllegalArgumentException("Token local do Financiador obrigatório e em formato válido");
        }
        if (!timeoutValido(timeoutConexao) || !timeoutValido(timeoutLeitura)) {
            throw new IllegalArgumentException("Timeouts devem ser positivos, em milissegundos, até 2147483647ms");
        }
    }

    private static boolean timeoutValido(Duration timeout) {
        return timeout != null && timeout.compareTo(Duration.ofMillis(1)) >= 0
                && timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0;
    }

    @Override public String toString() { return "ConexaoSignataria[credencial omitida]"; }
}
