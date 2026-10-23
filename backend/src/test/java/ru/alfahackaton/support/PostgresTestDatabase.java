package ru.alfahackaton.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

/** Each real PostgreSQL test owns a new schema; it never resets a shared database. */
public final class PostgresTestDatabase implements AutoCloseable {
    private final DriverManagerDataSource admin;
    private final DriverManagerDataSource dataSource;
    private final String schema = "test_" + UUID.randomUUID().toString().replace("-", "");

    public PostgresTestDatabase() {
        String url = System.getenv("TEST_DATABASE_URL");
        boolean available = url != null && !url.isBlank();
        if (!available && Boolean.parseBoolean(System.getenv("REQUIRE_POSTGRES_TESTS"))) {
            throw new IllegalStateException("TEST_DATABASE_URL is required by REQUIRE_POSTGRES_TESTS");
        }
        Assumptions.assumeTrue(available, "Set TEST_DATABASE_URL to run real PostgreSQL tests");
        String username = System.getenv().getOrDefault("TEST_DATABASE_USER", "postgres");
        String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "postgres");
        admin = new DriverManagerDataSource(url, username, password);
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(
                url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                username, password);
    }

    public DataSource dataSource() { return dataSource; }
    public JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }

    public Flyway flyway() {
        return Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema)
                .baselineOnMigrate(true).baselineVersion("0").locations("classpath:db/migration").load();
    }

    public void migrate() { flyway().migrate(); }

    @Override
    public void close() {
        new JdbcTemplate(admin).execute("DROP SCHEMA " + schema + " CASCADE");
    }
}
