package br.com.duplicataescritural.signataria;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "signataria.escrituracao.processamento.habilitado=false")
class SignatariaApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Test
    void documentacaoPermaneceDesabilitadaEBloqueadaSemPerfilLocal() throws Exception {
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        try (var client = HttpClient.newHttpClient()) {
            for (String caminho : java.util.List.of("/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html",
                    "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js")) {
                var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + caminho)).GET().build();
                assertThat(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            }
        }
    }

    @Test
    void rotasDeEscrituracaoPermanecemBloqueadasSemPerfilLocal() throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/escrituracoes/manuais"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            var autorizacao = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/autorizacoes-consulta"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            assertThat(client.send(autorizacao, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void contextoSobeComPostgresqlEFlyway() throws Exception {
        assertThat(POSTGRESQL.isRunning()).isTrue();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select to_regclass('escrituradora.flyway_schema_history') is not null")) {
            assertThat(connection.isValid(1)).isTrue();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getBoolean(1)).isTrue();
        }

        flyway.validate();
    }
}
