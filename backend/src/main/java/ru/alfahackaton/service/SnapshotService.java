package ru.alfahackaton.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.repository.EvidenceRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.provider.BkiDataProvider;
import ru.alfahackaton.provider.FnsDataProvider;
import ru.alfahackaton.provider.InternalFinanceDataProvider;
import ru.alfahackaton.risk.LoanRequest;
import ru.alfahackaton.risk.RiskInputs;
import ru.alfahackaton.risk.DemoRiskPolicy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class SnapshotService {
    private final InternalFinanceDataProvider internalProvider;
    private final BkiDataProvider bkiProvider;
    private final FnsDataProvider fnsProvider;
    private final UserRepository users;
    private final EvidenceRepository evidence;
    private final Clock clock;
    private final DemoRiskPolicy policy;
    private final LocalDate valuationDate;

    public SnapshotService(InternalFinanceDataProvider internalProvider, BkiDataProvider bkiProvider,
                           FnsDataProvider fnsProvider, UserRepository users, EvidenceRepository evidence,
                           DemoRiskPolicy policy, Clock clock, @Value("${demo.as-of:2026-10-23}") String valuationDate) {
        this.internalProvider = internalProvider;
        this.bkiProvider = bkiProvider;
        this.fnsProvider = fnsProvider;
        this.users = users;
        this.evidence = evidence;
        this.clock = clock;
        this.policy = policy;
        this.valuationDate = LocalDate.parse(valuationDate);
    }

    /** Caller owns the application transaction, including this insert and the durable job. */
    public ScoringSnapshot capture(ApplicationEntity application, String origin) {
        var internal = internalProvider.fetch(application.userId(), valuationDate);
        var bureau = bkiProvider.fetch(application.userId(), valuationDate);
        var fns = fnsProvider.fetch(application.userId(), valuationDate);
        BigDecimal totalDebt = internal.monthlyDebtRub() == null || bureau.monthlyDebtRub() == null
                ? null : internal.monthlyDebtRub().add(bureau.monthlyDebtRub());
        var inputs = new RiskInputs(valuationDate, internal.registeredOn(), internal.accountOpenedOn(),
                internal.directorChangedOn(), internal.soleFounderChangedOn(), internal.managementHistoryComplete(),
                internal.cashflows(), totalDebt, bureau.score(), bureau.maxOverdueDays24m(), bureau.recentClosedOverdueDays12m(),
                fns.taxBlocked(), fns.bankrupt(), fns.liquidating(), internal.source(), bureau.source(), fns.source());
        var user = users.findById(application.userId()).orElseThrow(() -> new IllegalStateException("Application owner is absent"));
        var snapshot = new ScoringSnapshot(true, policy.version(), null, null, user.companyName(), user.inn(), application.purpose(), inputs,
                new LoanRequest(BigDecimal.valueOf(application.amount()), application.termMonths()));
        evidence.insertSnapshot(application.id(), "DEMO_INPUTS_V1", valuationDate, OffsetDateTime.now(clock), origin, RiskJson.write(snapshot));
        return snapshot;
    }

    public Optional<ScoringSnapshot> load(long applicationId) {
        return evidence.findSnapshot(applicationId).map(document -> {
            if (!"DEMO_INPUTS_V1".equals(document.schemaVersion())) throw new IllegalStateException("Unsupported snapshot schema");
            var snapshot = RiskJson.read(document.payloadJson(), ScoringSnapshot.class);
            // Issue17's initial immutable fixture schema predates this explicit field and is defined for V1 only.
            if (snapshot.ruleVersion() == null) {
                return new ScoringSnapshot(snapshot.demoOnly(), "DEMO_RULES_V1", snapshot.scenarioCode(), snapshot.scenarioTitle(),
                        snapshot.companyName(), snapshot.inn(), snapshot.purpose(), snapshot.inputs(), snapshot.request());
            }
            return snapshot;
        });
    }

    public LocalDate valuationDate() { return valuationDate; }
}
