package ru.alfahackaton.persistance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import ru.alfahackaton.support.PostgresTestDatabase;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class PostgresMigrationTest {
    @Test
    void freshBootstrapPreservesLoginCodesAndRerunDoesNotDuplicateThem() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            db.migrate();
            assertEquals(3L, db.jdbc().queryForObject("SELECT count(*) FROM users", Long.class));
            assertEquals(0L, db.jdbc().queryForObject("SELECT count(*) FROM application_snapshots", Long.class));
            assertEquals(0L, db.jdbc().queryForObject("SELECT count(*) FROM processing_jobs", Long.class));
            assertEquals(1, db.flyway().info().applied().length);
        }
    }

    @Test
    void populatedLegacyDatabaseKeepsIdsNamesAmountsAndStatuses() {
        try (var db = new PostgresTestDatabase()) {
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql"),
                    new ClassPathResource("data.sql")).execute(db.dataSource());
            db.jdbc().update("UPDATE users SET company_name = 'Legacy name remains' WHERE username = 'client_good'");
            Long owner = db.jdbc().queryForObject("SELECT id FROM users WHERE username='client_good'", Long.class);
            Long id = db.jdbc().queryForObject("""
                    INSERT INTO applications(user_id,amount,term_months,purpose,status)
                    VALUES (?,1234567,12,'WORKING_CAPITAL','APPROVED') RETURNING id
                    """, Long.class, owner);
            db.migrate();
            db.migrate();
            assertEquals(owner, db.jdbc().queryForObject("SELECT id FROM users WHERE username='client_good'", Long.class));
            assertEquals("Legacy name remains", db.jdbc().queryForObject("SELECT company_name FROM users WHERE id=?", String.class, owner));
            assertEquals(1234567L, db.jdbc().queryForObject("SELECT amount FROM applications WHERE id=?", Long.class, id));
            assertEquals("APPROVED", db.jdbc().queryForObject("SELECT status FROM applications WHERE id=?", String.class, id));
            assertEquals(0L, db.jdbc().queryForObject("SELECT version FROM applications WHERE id=?", Long.class, id));
            assertEquals(0L, db.jdbc().queryForObject("SELECT count(*) FROM application_snapshots", Long.class));
        }
    }

    @Test
    void invalidMonetaryAndDuplicateMonthlyFactsAreRejectedByDatabase() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            Long owner = db.jdbc().queryForObject("SELECT id FROM users WHERE username='client_good'", Long.class);
            assertThrows(DataAccessException.class, () -> db.jdbc().update("""
                    INSERT INTO monthly_cashflows(user_id,month,turnover_rub,source,as_of)
                    VALUES (?,DATE '2026-09-01',-1,'SYNTHETIC_INTERNAL',DATE '2026-10-01')
                    """, owner));
            db.jdbc().update("""
                    INSERT INTO monthly_cashflows(user_id,month,turnover_rub,source,as_of)
                    VALUES (?,DATE '2026-09-01',1000000,'SYNTHETIC_INTERNAL',DATE '2026-10-01')
                    """, owner);
            assertThrows(DataAccessException.class, () -> db.jdbc().update("""
                    INSERT INTO monthly_cashflows(user_id,month,turnover_rub,source,as_of)
                    VALUES (?,DATE '2026-09-01',1,'SYNTHETIC_INTERNAL',DATE '2026-10-01')
                    """, owner));
        }
    }
}
