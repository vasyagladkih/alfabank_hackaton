package ru.alfahackaton.demo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import ru.alfahackaton.persistance.repository.EvidenceRepository;
import ru.alfahackaton.support.PostgresTestDatabase;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class DemoDataSeederTest {
    @Test
    void rerunKeepsDecisionsSnapshotHashesAndIdsWithoutDuplicates() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            var jdbc = JdbcClient.create(db.dataSource());
            var seeder = new DemoDataSeeder(jdbc, new EvidenceRepository(jdbc), new DataSourceTransactionManager(db.dataSource()));
            int count = seeder.seed();
            assertTrue(count >= 14, "The demo must include every required decision and hard-stop scenario");
            var before = db.jdbc().queryForList("SELECT a.id,a.demo_scenario_key,s.payload_sha256 FROM applications a JOIN application_snapshots s ON s.application_id=a.id ORDER BY a.id");
            Long id = db.jdbc().queryForObject("SELECT id FROM applications WHERE demo_scenario_key='demo-v1:manual-review'", Long.class);
            db.jdbc().update("UPDATE applications SET status='REJECTED',version=1,rejection_reason='Demo user decision' WHERE id=?", id);
            assertEquals(0, seeder.seed());
            assertEquals(before, db.jdbc().queryForList("SELECT a.id,a.demo_scenario_key,s.payload_sha256 FROM applications a JOIN application_snapshots s ON s.application_id=a.id ORDER BY a.id"));
            assertEquals("REJECTED", db.jdbc().queryForObject("SELECT status FROM applications WHERE id=?", String.class, id));
            assertEquals(3L, db.jdbc().queryForObject("SELECT count(*) FROM users", Long.class));
            assertEquals((long) count, db.jdbc().queryForObject("SELECT count(*) FROM application_audit", Long.class));
            assertEquals((long) count, db.jdbc().queryForObject("SELECT count(*) FROM processing_jobs", Long.class));
            assertEquals(24L, db.jdbc().queryForObject("SELECT count(*) FROM monthly_cashflows", Long.class));
        }
    }

    @Test
    void twoFreshDatabasesHaveIdenticalFixedInputsAndCreationTimes() {
        try (var first = new PostgresTestDatabase(); var second = new PostgresTestDatabase()) {
            first.migrate();
            second.migrate();
            seed(first);
            seed(second);
            assertEquals(documents(first), documents(second));
            assertEquals("2026-10-23", first.jdbc().queryForObject("SELECT min(as_of)::text FROM application_snapshots", String.class));
        }
    }

    private static void seed(PostgresTestDatabase db) {
        var jdbc = JdbcClient.create(db.dataSource());
        new DemoDataSeeder(jdbc, new EvidenceRepository(jdbc), new DataSourceTransactionManager(db.dataSource())).seed();
    }

    private static List<Map<String, Object>> documents(PostgresTestDatabase db) {
        return db.jdbc().queryForList("""
                SELECT a.demo_scenario_key,a.amount,a.term_months,a.created_at,s.payload_sha256,s.payload_json::text
                FROM applications a JOIN application_snapshots s ON a.id=s.application_id ORDER BY a.demo_scenario_key
                """);
    }
}
