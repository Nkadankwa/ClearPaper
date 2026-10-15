package org.example.clearpaper;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class PostgresMigrationIT {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test
    void postgresSchemaMigratesAndStaysPrivate() {
        assertTrue(jdbc.queryForObject("SELECT version()", String.class).startsWith("PostgreSQL"));
        assertEquals(4, flyway.info().applied().length);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals("clearpaper", jdbc.queryForObject("SELECT current_schema()", String.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='clearpaper' AND table_name='app_users' AND column_name='password_hash'",
                        Integer.class));
    }
}
