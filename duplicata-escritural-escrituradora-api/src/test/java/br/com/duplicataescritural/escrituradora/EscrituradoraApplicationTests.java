package br.com.duplicataescritural.escrituradora;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class EscrituradoraApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

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
