package ru.alfahackaton.persistance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.alfahackaton.persistance.repository.EvidenceRepository;
import ru.alfahackaton.support.PostgresTestDatabase;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class EvidenceRepositoryTest {
    @Test
    void snapshotIsIndependentOfLaterProfileChangesAndCannotBeRewritten() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            Long owner = db.jdbc().queryForObject("SELECT id FROM users WHERE username='client_good'", Long.class);
            Long id = db.jdbc().queryForObject("""
                    INSERT INTO applications(user_id,amount,term_months,purpose,status)
                    VALUES (?,1000000,12,'WORKING_CAPITAL','PROCESSING') RETURNING id
                    """, Long.class, owner);
            var repository = new EvidenceRepository(JdbcClient.create(db.dataSource()));
            repository.insertSnapshot(id, "demo-input-v1", LocalDate.parse("2026-10-23"),
                    OffsetDateTime.parse("2026-10-23T12:00:00Z"), "DEMO_PROFILE",
                    "{\"turnover\":1250000.50,\"company\":\"Synthetic original\",\"missing\":null}");
            db.jdbc().update("UPDATE users SET company_name='Changed later' WHERE id=?", owner);
            var snapshot = repository.findSnapshot(id).orElseThrow();
            assertTrue(snapshot.payloadJson().contains("Synthetic original"));
            assertTrue(snapshot.payloadJson().contains("1250000.5"));
            assertTrue(snapshot.payloadJson().contains("\"missing\":null"));
            assertEquals(64, snapshot.payloadSha256().length());
            assertThrows(DataAccessException.class, () -> db.jdbc().update(
                    "UPDATE application_snapshots SET payload_json='{}'::jsonb WHERE application_id=?", id));
            assertThrows(DataAccessException.class, () -> db.jdbc().update(
                    "DELETE FROM application_snapshots WHERE application_id=?", id));
            assertThrows(DataAccessException.class, () -> repository.insertSnapshot(id, "demo-input-v1",
                    LocalDate.parse("2026-10-23"), OffsetDateTime.parse("2026-10-23T12:00:00Z"),
                    "DEMO_PROFILE", "{}"));
        }
    }

    @Test
    void resultAndAuditAreAppendOnlyAndAutomaticEventCannotBeDuplicated() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            Long owner = db.jdbc().queryForObject("SELECT id FROM users WHERE username='client_good'", Long.class);
            Long id = db.jdbc().queryForObject("""
                    INSERT INTO applications(user_id,amount,term_months,purpose,status)
                    VALUES (?,1000000,12,'WORKING_CAPITAL','PROCESSING') RETURNING id
                    """, Long.class, owner);
            var repository = new EvidenceRepository(JdbcClient.create(db.dataSource()));
            var at = OffsetDateTime.parse("2026-10-23T12:00:00Z");
            repository.insertResult(id, "demo-rules-v1", "{\"status\":\"MANUAL_REVIEW\"}", at);
            repository.appendAudit(id, "SCORED", "SCORED", "SYSTEM", null, "PROCESSING", "MANUAL_REVIEW",
                    1L, "Synthetic review", "{}", at);
            assertEquals("demo-rules-v1", repository.findResult(id).orElseThrow().ruleVersion());
            assertEquals(1, repository.findAudit(id).size());
            assertThrows(DataAccessException.class, () -> repository.appendAudit(id, "SCORED", "SCORED", "SYSTEM",
                    null, "PROCESSING", "MANUAL_REVIEW", 1L, "Duplicate", "{}", at));
            assertThrows(DataAccessException.class, () -> db.jdbc().update("DELETE FROM scoring_results WHERE application_id=?", id));
            assertThrows(DataAccessException.class, () -> db.jdbc().update("UPDATE application_audit SET rationale='rewrite' WHERE application_id=?", id));
        }
    }
}
