package ru.alfahackaton.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.EvidenceRepository;
import ru.alfahackaton.persistance.repository.ProcessingJobRepository;
import ru.alfahackaton.risk.Assessment;
import ru.alfahackaton.risk.LoanTerms;
import ru.alfahackaton.risk.Outcome;
import ru.alfahackaton.risk.RiskEngine;
import ru.alfahackaton.risk.RiskReason;
import ru.alfahackaton.risk.Severity;
import ru.alfahackaton.service.exception.ApplicationConflictException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** A short database transaction owns each CPU-only calculation. No task is kept only in memory. */
@Service
public class ScoringProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ScoringProcessor.class);
    private final ApplicationRepository applications;
    private final ProcessingJobRepository jobs;
    private final EvidenceRepository evidence;
    private final SnapshotService snapshots;
    private final RiskEngine engine;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int maximumAttempts;
    private final long retryDelayMs;

    public ScoringProcessor(ApplicationRepository applications, ProcessingJobRepository jobs, EvidenceRepository evidence,
                            SnapshotService snapshots, RiskEngine engine, PlatformTransactionManager transactionManager,
                            Clock clock, @Value("${demo.processing.max-attempts:3}") int maximumAttempts,
                            @Value("${demo.processing.retry-delay-ms:1000}") long retryDelayMs) {
        if (maximumAttempts < 1 || maximumAttempts > 10 || retryDelayMs < 1 || retryDelayMs > 60000) {
            throw new IllegalArgumentException("Demo worker retry bounds are invalid");
        }
        this.applications = applications;
        this.jobs = jobs;
        this.evidence = evidence;
        this.snapshots = snapshots;
        this.engine = engine;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maximumAttempts = maximumAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    public int processBatch(int maximumItems) {
        if (maximumItems < 1 || maximumItems > 64) throw new IllegalArgumentException("Demo batch must contain 1–64 items");
        int processed = 0;
        while (processed < maximumItems && processOne()) processed++;
        return processed;
    }

    public boolean processOne() {
        long[] claimed = {-1};
        try {
            return Boolean.TRUE.equals(transactions.execute(tx -> {
                var next = jobs.lockNextReady(OffsetDateTime.now(clock));
                if (next.isEmpty()) return false;
                claimed[0] = next.get();
                var application = applications.findById(next.get()).orElseThrow();
                var snapshot = snapshots.load(application.id()).orElseGet(() -> snapshots.capture(application, "LEGACY_RECOVERY"));
                if (!snapshot.demoOnly() || !engine.policy().version().equals(snapshot.ruleVersion())) {
                    throw new IllegalStateException("Unsupported snapshot rule version or source mode");
                }
                if (snapshot.request() == null || snapshot.request().amountRub().compareTo(BigDecimal.valueOf(application.amount())) != 0
                        || snapshot.request().termMonths() != application.termMonths()) {
                    throw new IllegalStateException("Snapshot request does not match immutable application terms");
                }
                Assessment assessment = engine.assess(snapshot.inputs(), snapshot.request());
                LoanTerms terms = assessment.outcome() == Outcome.APPROVED
                        ? new LoanTerms(snapshot.request().amountRub(), snapshot.request().termMonths(), assessment.annualRatePercent())
                        : assessment.offer();
                finish(application, assessment, terms, OffsetDateTime.now(clock));
                return true;
            }));
        } catch (RuntimeException failure) {
            LOG.warn("Demo scoring attempt failed for application {} ({})", claimed[0], failure.getClass().getSimpleName());
            LOG.debug("Demo scoring failure details", failure);
            if (claimed[0] < 0) return false;
            recordFailure(claimed[0]);
            return true;
        }
    }

    private void finish(ApplicationEntity application, Assessment assessment, LoanTerms terms, OffsetDateTime now) {
        String summary = assessment.reasons().stream().map(RiskReason::message).collect(Collectors.joining(" "));
        evidence.insertResult(application.id(), assessment.ruleVersion(), RiskJson.write(assessment), now);
        boolean changed = applications.transition(application.id(), application.version(), assessment.outcome().name(), summary,
                terms == null ? null : terms.amountRub(), terms == null ? null : terms.termMonths(),
                terms == null ? null : terms.annualRatePercent(), terms == null ? null : RiskEngine.monthlyPayment(terms), now);
        if (!changed) throw new ApplicationConflictException("Заявка уже изменилась");
        evidence.appendAudit(application.id(), "SCORED", "DEMO_SCORED", "SYSTEM", null, application.status(),
                assessment.outcome().name(), application.version() + 1, summary,
                "{\"demoOnly\":true,\"ruleVersion\":\"" + assessment.ruleVersion() + "\"}", now);
        jobs.complete(application.id(), now);
    }

    private void recordFailure(long applicationId) {
        try {
            transactions.executeWithoutResult(tx -> {
                var application = applications.lockById(applicationId).orElseThrow();
                if (!"PROCESSING".equals(application.status())) return;
                OffsetDateTime now = OffsetDateTime.now(clock);
                int attempts = jobs.recordFailure(applicationId, "SCORING_ATTEMPT_FAILED", now.plusNanos(retryDelayMs * 1000000L));
                if (attempts < maximumAttempts) return;
                var failed = new Assessment(engine.policy().version(), snapshots.valuationDate(), Outcome.MANUAL_REVIEW,
                        null, null, List.of(), List.of(new RiskReason("PROCESSING_FAILED", Severity.DATA_QUALITY,
                        "Учебную обработку не удалось завершить после ограниченных повторов; требуется рассмотрение сотрудника.")),
                        false, false, null, null, null, null, null, null, null);
                finish(application, failed, null, now);
            });
        } catch (RuntimeException storageFailure) {
            // A database outage cannot persist bookkeeping. The original PROCESSING job remains recoverable.
            LOG.warn("Demo retry bookkeeping unavailable for application {} ({})", applicationId, storageFailure.getClass().getSimpleName());
        }
    }
}
