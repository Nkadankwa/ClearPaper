package org.example.clearpaper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsCreateSchemaAndAreRepeatable() {
        assertEquals(3, flyway.info().applied().length);
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Long.class));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }
}
