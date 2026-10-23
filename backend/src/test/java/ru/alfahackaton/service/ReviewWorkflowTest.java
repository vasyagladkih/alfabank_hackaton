package ru.alfahackaton.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import ru.alfahackaton.dto.*;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.repository.MutationRequestRepository;
import ru.alfahackaton.risk.RiskEngine;
import ru.alfahackaton.risk.LoanRequest;
import ru.alfahackaton.service.exception.ApplicationConflictException;
import ru.alfahackaton.service.exception.ApplicationNotFoundException;
import ru.alfahackaton.support.PostgresTestDatabase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class ReviewWorkflowTest {
    private static ReviewWorkflowService review(WorkflowTestSupport work) { return review(work, work.engine); }
    private static ReviewWorkflowService review(WorkflowTestSupport work, RiskEngine engine) {
        return new ReviewWorkflowService(work.applications, work.users, work.snapshots, work.evidence,
                new MutationRequestRepository(work.jdbc), work.mapper, new EmployeeDossierMapper(work.mapper, engine.policy()),
                engine, work.transactions, work.clock);
    }
    private static WorkflowTestSupport completed(PostgresTestDatabase db) {
        db.migrate();
        var work = new WorkflowTestSupport(db);
        work.seed();
        work.processor().processBatch(64);
        return work;
    }
    private static long scenario(WorkflowTestSupport work, String code) {
        return work.db.jdbc().queryForObject("SELECT id FROM applications WHERE demo_scenario_key=?", Long.class, "demo-v1:" + code);
    }
    private static EmployeeDecisionRequest command(long version, EmployeeDecisionType type, Long amount) {
        var request = new EmployeeDecisionRequest();
        request.setExpectedVersion(version);
        request.setRequestId(UUID.randomUUID());
        request.setType(type);
        request.setRationale("Проверены синтетические исходные данные");
        request.setOfferAmount(amount);
        return request;
    }
    private static OfferResponseRequest consent(long version, OfferResponseRequest.DecisionEnum decision) {
        var request = new OfferResponseRequest();
        request.setExpectedVersion(version);
        request.setRequestId(UUID.randomUUID());
        request.setDecision(decision);
        return request;
    }

    @Test
    void employeeApprovalIsVersionedAndExactReplayPreservesEvidence() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            var review = review(work);
            long id = scenario(work, "manual-review"), employee = work.owner("bank_employee");
            var snapshot = work.evidence.findSnapshot(id).orElseThrow();
            var result = work.evidence.findResult(id).orElseThrow();
            var before = review.getDossier(id, employee);
            assertEquals(640, before.getAssessment().getScore());
            assertEquals(850, before.getAssessment().getMaxScore());
            assertTrue(before.getAllowedActions().contains(EmployeeDecisionType.APPROVE));
            assertEquals(12, before.getCashflows().size());
            assertTrue(before.getSources().stream().allMatch(s -> s.getDemoOnly() && s.getFreshness() == DossierSource.FreshnessEnum.FRESH));
            var request = command(1, EmployeeDecisionType.APPROVE, null);
            var approved = review.decide(id, request, employee);
            assertEquals(ApplicationStatus.APPROVED, approved.getApplication().getStatus());
            assertEquals(2L, approved.getApplication().getVersion());
            assertEquals(1000000L, approved.getApplication().getAmount());
            assertEquals(1000000L, approved.getApplication().getOffer().getAmount());
            assertEquals(approved, review.decide(id, request, employee));
            request.setRationale("Другая причина под уже использованным ключом");
            assertThrows(ApplicationConflictException.class, () -> review.decide(id, request, employee));
            assertThrows(ApplicationConflictException.class, () -> review.decide(id, command(2, EmployeeDecisionType.REJECT, null), employee));
            assertEquals(3, work.evidence.findAudit(id).size());
            assertEquals(snapshot, work.evidence.findSnapshot(id).orElseThrow());
            assertEquals(result, work.evidence.findResult(id).orElseThrow());
        }
    }

    @Test
    void incompleteAndStaleInputsAllowOnlyReasonedRejection() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            var review = review(work);
            long employee = work.owner("bank_employee");
            for (String code : List.of("missing-data", "stale-data")) {
                long id = scenario(work, code);
                var dossier = review.getDossier(id, employee);
                assertNull(dossier.getAssessment().getScore());
                assertEquals(List.of(EmployeeDecisionType.REJECT), dossier.getAllowedActions());
                assertThrows(ApplicationConflictException.class, () -> review.decide(id, command(1, EmployeeDecisionType.APPROVE, null), employee));
                assertThrows(ApplicationConflictException.class, () -> review.decide(id, command(1, EmployeeDecisionType.COUNTEROFFER, 100000L), employee));
                assertEquals(1L, work.applications.findById(id).orElseThrow().version());
                var rejected = review.decide(id, command(1, EmployeeDecisionType.REJECT, null), employee);
                assertEquals(ApplicationStatus.REJECTED, rejected.getApplication().getStatus());
                assertEquals(3, rejected.getAudit().size());
                assertNull(rejected.getAssessment().getScore());
            }
            assertTrue(review.getDossier(scenario(work, "stale-data"), employee).getSources().stream()
                    .anyMatch(s -> s.getFreshness() == DossierSource.FreshnessEnum.STALE));
        }
    }

    @Test
    void employeeOfferRequiresOwnerConsentAndReplaysTheOriginalResponseAfterAcceptance() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            var review = review(work);
            long id = scenario(work, "manual-review"), employee = work.owner("bank_employee");
            long owner = work.applications.findById(id).orElseThrow().userId();
            var offer = command(1, EmployeeDecisionType.COUNTEROFFER, 500000L);
            var proposed = review.decide(id, offer, employee);
            assertEquals(ApplicationStatus.OFFER_PROPOSED, proposed.getApplication().getStatus());
            assertEquals(1000000L, proposed.getApplication().getAmount());
            assertEquals(500000L, proposed.getApplication().getOffer().getAmount());
            var accept = consent(2, OfferResponseRequest.DecisionEnum.ACCEPT);
            assertThrows(AccessDeniedException.class, () -> review.respondToOffer(id, accept, work.owner("client_bad")));
            assertThrows(AccessDeniedException.class, () -> review.respondToOffer(id, accept, employee));
            var accepted = review.respondToOffer(id, accept, owner);
            assertEquals(ApplicationStatus.APPROVED, accepted.getStatus());
            assertEquals(3L, accepted.getVersion());
            assertEquals(accepted, review.respondToOffer(id, accept, owner));
            assertEquals(proposed, review.decide(id, offer, employee));
            accept.setDecision(OfferResponseRequest.DecisionEnum.REJECT);
            assertThrows(ApplicationConflictException.class, () -> review.respondToOffer(id, accept, owner));
            assertEquals(4, work.evidence.findAudit(id).size());
            assertEquals("CLIENT_OFFER_ACCEPTED", work.evidence.findAudit(id).getLast().eventType());
        }
    }

    @Test
    void clientRejectsAutomaticOfferWithoutRewritingOriginalRiskAssessment() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            var review = review(work);
            long id = scenario(work, "counteroffer"), owner = work.applications.findById(id).orElseThrow().userId();
            var original = work.evidence.findResult(id).orElseThrow();
            var request = consent(1, OfferResponseRequest.DecisionEnum.REJECT);
            var rejected = review.respondToOffer(id, request, owner);
            assertEquals(ApplicationStatus.REJECTED, rejected.getStatus());
            assertEquals(10000000L, rejected.getAmount());
            assertTrue(rejected.getRejectionReason().contains("Клиент"));
            assertEquals(rejected, review.respondToOffer(id, request, owner));
            assertThrows(ApplicationConflictException.class, () -> review.respondToOffer(id, consent(2, OfferResponseRequest.DecisionEnum.ACCEPT), owner));
            assertEquals(original, work.evidence.findResult(id).orElseThrow());
            assertEquals("CLIENT_OFFER_REJECTED", work.evidence.findAudit(id).getLast().eventType());
        }
    }

    @Test
    void serverRechecksOriginalAmountAndAffordableCapForManualOffers() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            long owner = work.owner("client_good"), employee = work.owner("bank_employee");
            db.jdbc().update("UPDATE company_profiles SET director_changed_on=DATE '2026-10-01' WHERE user_id=?", owner);
            var created = work.service.createApplication(new CreateApplicationRequest(10000000L, 12, LoanPurpose.WORKING_CAPITAL), owner);
            work.processor().processOne();
            var review = review(work);
            var dossier = review.getDossier(created.getId(), employee);
            assertEquals(ApplicationStatus.MANUAL_REVIEW, dossier.getApplication().getStatus());
            assertFalse(dossier.getAllowedActions().contains(EmployeeDecisionType.APPROVE));
            assertTrue(dossier.getAllowedActions().contains(EmployeeDecisionType.COUNTEROFFER));
            long cap = dossier.getAssessment().getAffordableAmount();
            assertThrows(ApplicationConflictException.class, () -> review.decide(created.getId(), command(1, EmployeeDecisionType.APPROVE, null), employee));
            assertThrows(IllegalArgumentException.class, () -> review.decide(created.getId(), command(1, EmployeeDecisionType.COUNTEROFFER, cap + 1), employee));
            assertThrows(IllegalArgumentException.class, () -> review.decide(created.getId(), command(1, EmployeeDecisionType.COUNTEROFFER, 10000001L), employee));
            assertEquals(1L, work.applications.findById(created.getId()).orElseThrow().version());
            assertEquals(2, work.evidence.findAudit(created.getId()).size());
            var offered = review.decide(created.getId(), command(1, EmployeeDecisionType.COUNTEROFFER, cap), employee);
            assertEquals(cap, offered.getApplication().getOffer().getAmount());
            assertEquals(12, offered.getApplication().getOffer().getTermMonths());
            assertEquals(new BigDecimal("16.5"), new BigDecimal(offered.getApplication().getOffer().getAnnualRate()));
            assertEquals(ApplicationStatus.APPROVED, review.respondToOffer(created.getId(), consent(2, OfferResponseRequest.DecisionEnum.ACCEPT), owner).getStatus());
        }
    }

    @Test
    void competingEmployeeCommandsCommitExactlyOneTransition() throws Exception {
        try (var db = new PostgresTestDatabase(); var pool = Executors.newFixedThreadPool(2)) {
            var work = completed(db);
            long id = scenario(work, "manual-review"), employee = work.owner("bank_employee");
            var start = new CountDownLatch(1);
            var tasks = List.of(EmployeeDecisionType.APPROVE, EmployeeDecisionType.REJECT).stream().map(type -> pool.submit(() -> {
                start.await();
                try { review(new WorkflowTestSupport(db)).decide(id, command(1, type, null), employee); return "OK"; }
                catch (ApplicationConflictException conflict) { return "CONFLICT"; }
            })).toList();
            start.countDown();
            var first = tasks.get(0).get(30, TimeUnit.SECONDS);
            var second = tasks.get(1).get(30, TimeUnit.SECONDS);
            assertEquals(1, List.of(first, second).stream().filter("OK"::equals).count());
            assertEquals(1, List.of(first, second).stream().filter("CONFLICT"::equals).count());
            assertEquals(2L, work.applications.findById(id).orElseThrow().version());
            assertEquals(3, work.evidence.findAudit(id).size());
            assertEquals(1L, db.jdbc().queryForObject("SELECT count(*) FROM mutation_requests WHERE application_id=?", Long.class, id));
        }
    }

    @Test
    void receiptFailureRollsBackStatusAuditAndVersionBeforeRetry() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            long id = scenario(work, "manual-review"), employee = work.owner("bank_employee");
            var review = review(work);
            var request = command(1, EmployeeDecisionType.APPROVE, null);
            db.jdbc().execute("CREATE FUNCTION test_fail_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected receipt failure'; END $$");
            db.jdbc().execute("CREATE TRIGGER test_fail_receipt BEFORE INSERT ON mutation_requests FOR EACH ROW EXECUTE FUNCTION test_fail_receipt()");
            assertThrows(RuntimeException.class, () -> review.decide(id, request, employee));
            assertEquals("MANUAL_REVIEW", work.applications.findById(id).orElseThrow().status());
            assertEquals(1L, work.applications.findById(id).orElseThrow().version());
            assertEquals(2, work.evidence.findAudit(id).size());
            assertEquals(0L, db.jdbc().queryForObject("SELECT count(*) FROM mutation_requests", Long.class));
            db.jdbc().execute("DROP TRIGGER test_fail_receipt ON mutation_requests");
            assertEquals(ApplicationStatus.APPROVED, review.decide(id, request, employee).getApplication().getStatus());
            assertEquals(3, work.evidence.findAudit(id).size());
        }
    }

    @Test
    void rolesValidationLiteralSearchAndMissingApplicationsAreEnforced() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            var review = review(work);
            long id = scenario(work, "manual-review"), employee = work.owner("bank_employee"), client = work.owner("client_good");
            assertThrows(AccessDeniedException.class, () -> review.getDossier(id, client));
            assertThrows(AccessDeniedException.class, () -> review.listApplications(null, null, client));
            assertThrows(AccessDeniedException.class, () -> review.decide(id, command(1, EmployeeDecisionType.REJECT, null), client));
            assertThrows(ApplicationNotFoundException.class, () -> review.getDossier(Long.MAX_VALUE, employee));
            assertThrows(ApplicationNotFoundException.class, () -> review.respondToOffer(Long.MAX_VALUE, consent(1, OfferResponseRequest.DecisionEnum.REJECT), client));
            assertEquals(21, review.listApplications(null, null, employee).size());
            assertTrue(review.listApplications(ApplicationStatus.MANUAL_REVIEW, String.valueOf(id), employee).stream().anyMatch(a -> a.getId() == id));
            assertTrue(review.listApplications(null, "%' OR true --", employee).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> review.listApplications(null, "x".repeat(121), employee));
            var bad = command(1, EmployeeDecisionType.REJECT, null);
            bad.setRationale("   ");
            assertThrows(IllegalArgumentException.class, () -> review.decide(id, bad, employee));
            assertThrows(IllegalArgumentException.class, () -> review.decide(id, command(1, EmployeeDecisionType.APPROVE, 100000L), employee));
            assertThrows(IllegalArgumentException.class, () -> review.decide(id, command(1, EmployeeDecisionType.COUNTEROFFER, null), employee));
            assertEquals(2, work.evidence.findAudit(id).size());
        }
    }

    @Test
    void unsupportedRuleVersionCannotReinterpretSavedEvidenceForApproval() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            var nextRules = new RiskEngine(work.engine.policy().withRates("DEMO_RULES_V2", new BigDecimal("16.5"), new BigDecimal("18.5")));
            var review = review(work, nextRules);
            long id = scenario(work, "manual-review"), employee = work.owner("bank_employee");
            assertEquals(List.of(EmployeeDecisionType.REJECT), review.getDossier(id, employee).getAllowedActions());
            assertThrows(ApplicationConflictException.class, () -> review.decide(id, command(1, EmployeeDecisionType.APPROVE, null), employee));
            assertEquals("DEMO_RULES_V1", review.getDossier(id, employee).getAssessment().getRuleVersion());
        }
    }

    @Test
    void pendingAndLegacyDossiersNeverInventAnAssessment() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            var work = new WorkflowTestSupport(db);
            work.seed();
            var review = review(work);
            long employee = work.owner("bank_employee");
            var pending = review.getDossier(scenario(work, "approved"), employee);
            assertEquals(ApplicationStatus.PROCESSING, pending.getApplication().getStatus());
            assertNull(pending.getAssessment());
            assertTrue(pending.getAllowedActions().isEmpty());
            assertEquals(12, pending.getCashflows().size());
            var now = OffsetDateTime.now(work.clock);
            var legacy = work.applications.save(new ApplicationEntity(null, work.owner("client_good"), 1000000L, 12,
                    "WORKING_CAPITAL", "APPROVED", null, now, now));
            var historical = review.getDossier(legacy.id(), employee);
            assertNull(historical.getAssessment());
            assertNull(historical.getSnapshotSha256());
            assertTrue(historical.getFacts().isEmpty());
            assertTrue(historical.getCashflows().isEmpty());
            assertTrue(historical.getAllowedActions().isEmpty());
            assertTrue(historical.getSources().stream().allMatch(s -> s.getFreshness() == DossierSource.FreshnessEnum.MISSING));
        }
    }

    @Test
    void requestIdsAreScopedToApplicationAndTrimmedRationaleIsCanonical() {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            var review = review(work);
            long firstId = scenario(work, "manual-review"), secondId = scenario(work, "director-59-days");
            long employee = work.owner("bank_employee");
            var request = command(1, EmployeeDecisionType.REJECT, null);
            request.setRationale("  Обоснованный учебный отказ  ");
            var first = review.decide(firstId, request, employee);
            request.setRationale("Обоснованный учебный отказ");
            assertEquals(first, review.decide(firstId, request, employee));
            var second = review.decide(secondId, request, employee);
            assertEquals(ApplicationStatus.REJECTED, second.getApplication().getStatus());
            assertEquals(2L, db.jdbc().queryForObject("SELECT count(*) FROM mutation_requests WHERE request_id=?", Long.class, request.getRequestId()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNSUPPORTED", "MALFORMED", "MISSING_INPUTS", "MISSING_AS_OF"})
    void unreadableSnapshotsCanBeExplainedAndRejectedAfterBoundedProcessingRetries(String mode) {
        try (var db = new PostgresTestDatabase()) {
            var work = completed(db);
            var now = OffsetDateTime.now(work.clock);
            var application = work.applications.save(new ApplicationEntity(null, work.owner("client_good"), 1000000L, 12,
                    "WORKING_CAPITAL", "PROCESSING", null, now, now));
            String payload = "{\"demoOnly\":true,\"ruleVersion\":\"DEMO_RULES_V1\",\"inputs\":\"unreadable\"}";
            if ("MISSING_INPUTS".equals(mode)) payload = RiskJson.write(new ScoringSnapshot(true, "DEMO_RULES_V1", null, null,
                    null, null, "WORKING_CAPITAL", null, new LoanRequest(new BigDecimal("1000000"), 12)));
            if ("MISSING_AS_OF".equals(mode)) payload = work.evidence.findSnapshot(scenario(work, "approved")).orElseThrow().payloadJson()
                    .replace("\"asOf\":\"2026-10-23\"", "\"asOf\":null");
            work.evidence.insertSnapshot(application.id(), "UNSUPPORTED".equals(mode) ? "UNSUPPORTED" : "DEMO_INPUTS_V1",
                    work.snapshots.valuationDate(), now, "LEGACY_RECOVERY", payload);
            var originalSnapshot = work.evidence.findSnapshot(application.id()).orElseThrow();
            work.jobs.enqueue(application.id(), now);
            for (int attempt = 0; attempt < 3; attempt++) {
                if (!"PROCESSING".equals(work.applications.findById(application.id()).orElseThrow().status())) break;
                db.jdbc().update("UPDATE processing_jobs SET available_at=? WHERE application_id=?", now, application.id());
                assertTrue(work.processor().processOne());
            }
            assertEquals("MANUAL_REVIEW", work.applications.findById(application.id()).orElseThrow().status());
            var originalResult = work.evidence.findResult(application.id()).orElseThrow();
            var review = review(work);
            long employee = work.owner("bank_employee");
            var dossier = review.getDossier(application.id(), employee);
            assertEquals(List.of(EmployeeDecisionType.REJECT), dossier.getAllowedActions());
            if (mode.startsWith("MISSING")) assertNull(dossier.getAssessment());
            else assertNull(dossier.getAssessment().getScore());
            assertEquals(originalSnapshot.payloadSha256(), dossier.getSnapshotSha256());
            assertTrue(dossier.getSources().stream().allMatch(s -> s.getFreshness() == DossierSource.FreshnessEnum.INVALID));
            assertTrue(dossier.getFacts().stream().anyMatch(f -> "snapshotReadError".equals(f.getCode())));
            assertEquals(ApplicationStatus.REJECTED, review.decide(application.id(), command(1, EmployeeDecisionType.REJECT, null), employee).getApplication().getStatus());
            assertEquals(originalSnapshot, work.evidence.findSnapshot(application.id()).orElseThrow());
            assertEquals(originalResult, work.evidence.findResult(application.id()).orElseThrow());
        }
    }
}
