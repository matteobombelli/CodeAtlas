package dev.springbootstaticanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesFoundationSchema() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_metadata WHERE metadata_key = 'schema_version'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
