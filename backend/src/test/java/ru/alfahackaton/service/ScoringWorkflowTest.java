package ru.alfahackaton.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.alfahackaton.demo.DemoSeedCatalog;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.dto.LoanPurpose;
import ru.alfahackaton.support.PostgresTestDatabase;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class ScoringWorkflowTest {
    @ParameterizedTest
    @ValueSource(strings = {"2026-10-24", "2026-09-07"})
    void oneInvalidInternalComponentCannotBeHiddenByCurrentRecords(String observedOn) {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            var work = new WorkflowTestSupport(db);
            work.seed();
            work.processor().processBatch(64);
            long owner = work.owner("client_good");
            db.jdbc().update("""
                    UPDATE monthly_cashflows SET as_of=CAST(? AS DATE)
                    WHERE user_id=? AND month=(SELECT min(month) FROM monthly_cashflows WHERE user_id=?)
                    """, observedOn, owner, owner);
            var created = work.service.createApplication(
                    new CreateApplicationRequest(1000000L, 12, LoanPurpose.WORKING_CAPITAL), owner);
            assertTrue(work.processor().processOne());
            assertEquals("MANUAL_REVIEW", work.applications.findById(created.getId()).orElseThrow().status());
            assertEquals(LocalDate.parse(observedOn), work.snapshots.load(created.getId()).orElseThrow().inputs().internalSource().observedOn());
        }
    }

    @Test
    void allSeededScenariosCompleteOnceWithTheirDeclaredOutcomes() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            var work = new WorkflowTestSupport(db);
            int count = work.seed();
            assertEquals(count, work.processor().processBatch(64));
            assertEquals(0, work.processor().processBatch(64));
            for (var scenario : DemoSeedCatalog.load().scenarios()) {
                assertEquals(scenario.expectedOutcome(), db.jdbc().queryForObject(
                        "SELECT status FROM applications WHERE demo_scenario_key=?", String.class, "demo-v1:" + scenario.code()), scenario.code());
            }
            assertEquals((long) count, db.jdbc().queryForObject("SELECT count(*) FROM scoring_results", Long.class));
            assertEquals((long) count * 2, db.jdbc().queryForObject("SELECT count(*) FROM application_audit", Long.class));
            assertEquals(0L, db.jdbc().queryForObject("SELECT count(*) FROM applications WHERE version<>1", Long.class));
        }
    }

    @Test
    void clientCreationAtomicallyCapturesProvidersAndSurvivesProcessorRecreation() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            var work = new WorkflowTestSupport(db);
            work.seed();
            work.processor().processBatch(64);
            var request = new CreateApplicationRequest(1000000L, 12, LoanPurpose.WORKING_CAPITAL);
            var created = work.service.createApplication(request, work.owner("client_good"));
            assertEquals("PROCESSING", created.getStatus().getValue());
            assertEquals(0L, created.getVersion());
            assertTrue(work.evidence.findSnapshot(created.getId()).isPresent());
            assertEquals(1L, db.jdbc().queryForObject("SELECT count(*) FROM processing_jobs WHERE application_id=?", Long.class, created.getId()));
            db.jdbc().update("UPDATE synthetic_registry_facts SET tax_blocked=TRUE WHERE user_id=?", work.owner("client_good"));
            // A new service instance has no in-memory task; it recovers the committed database job.
            assertTrue(new WorkflowTestSupport(db).processor().processOne());
            assertEquals("APPROVED", work.applications.findById(created.getId()).orElseThrow().status());
            var subsequent = work.service.createApplication(request, work.owner("client_good"));
            work.processor().processOne();
            assertEquals("REJECTED", work.applications.findById(subsequent.getId()).orElseThrow().status());
            assertEquals(2, work.evidence.findAudit(created.getId()).size());
        }
    }

    @Test
    void rolledBackCompletionHasNoOrphanResultAndRetryAddsExactlyOneDecisionAudit() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            var work = new WorkflowTestSupport(db);
            work.seed();
            long id = db.jdbc().queryForObject("SELECT min(id) FROM applications", Long.class);
            db.jdbc().execute("""
                    CREATE FUNCTION test_fail_completion() RETURNS TRIGGER LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'Injected transaction failure'; END $$
                    """);
            db.jdbc().execute("CREATE TRIGGER test_fail_completion BEFORE UPDATE ON applications FOR EACH ROW EXECUTE FUNCTION test_fail_completion()");
            assertTrue(work.processor().processOne());
            assertTrue(work.evidence.findResult(id).isEmpty());
            assertEquals(1, work.evidence.findAudit(id).size());
            assertEquals("PROCESSING", work.applications.findById(id).orElseThrow().status());
            assertEquals(1, db.jdbc().queryForObject("SELECT attempts FROM processing_jobs WHERE application_id=?", Integer.class, id));
            db.jdbc().execute("DROP TRIGGER test_fail_completion ON applications");
            db.jdbc().update("UPDATE processing_jobs SET available_at=TIMESTAMPTZ '2026-10-23T11:00:00Z' WHERE application_id=?", id);
            assertTrue(work.processor().processOne());
            assertTrue(work.evidence.findResult(id).isPresent());
            assertEquals(2, work.evidence.findAudit(id).size());
            assertEquals(1L, work.applications.findById(id).orElseThrow().version());
        }
    }

    @Test
    void twoWorkersSkipLockedRowsAndNeverDuplicateEvidence() throws Exception {
        try (var db = new PostgresTestDatabase(); var pool = Executors.newFixedThreadPool(2)) {
            db.migrate();
            var work = new WorkflowTestSupport(db);
            int count = work.seed();
            var first = pool.submit(() -> work.processor().processBatch(64));
            var second = pool.submit(() -> new WorkflowTestSupport(db).processor().processBatch(64));
            assertEquals(count, first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS));
            assertEquals((long) count, db.jdbc().queryForObject("SELECT count(*) FROM scoring_results", Long.class));
            assertEquals((long) count * 2, db.jdbc().queryForObject("SELECT count(*) FROM application_audit", Long.class));
            assertEquals(0L, db.jdbc().queryForObject("SELECT count(*) FROM applications WHERE status='PROCESSING'", Long.class));
        }
    }
}
