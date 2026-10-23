package ru.alfahackaton.provider.mock;

import ru.alfahackaton.persistance.repository.SyntheticDataRepository;
import ru.alfahackaton.provider.InternalFinanceDataProvider;
import ru.alfahackaton.risk.MonthlyCashflow;
import ru.alfahackaton.risk.SourceEvidence;

import java.time.LocalDate;
import java.time.YearMonth;

/** Deterministic JDBC fixture adapter; it never reads actual bank transactions. */
public final class MockInternalFinanceDataProvider implements InternalFinanceDataProvider {
    private final SyntheticDataRepository repository;
    public MockInternalFinanceDataProvider(SyntheticDataRepository repository) { this.repository = repository; }

    @Override
    public Data fetch(long userId, LocalDate asOf) {
        YearMonth lastClosed = YearMonth.from(asOf).minusMonths(1);
        var rows = repository.findCashflows(userId).stream()
                .filter(row -> !YearMonth.from(row.month()).isBefore(lastClosed.minusMonths(11))
                        && !YearMonth.from(row.month()).isAfter(lastClosed)).toList();
        var flows = rows.stream().map(row -> new MonthlyCashflow(YearMonth.from(row.month()), row.turnoverRub())).toList();
        var profile = repository.findProfile(userId);
        if (profile.isEmpty()) return new Data(null, null, null, null, null, null, flows, null);
        var facts = profile.get();
        LocalDate oldest = facts.asOf();
        LocalDate newest = facts.asOf();
        for (var row : rows) {
            if (row.asOf().isBefore(oldest)) oldest = row.asOf();
            if (row.asOf().isAfter(newest)) newest = row.asOf();
        }
        // A future component invalidates the bundle; otherwise its oldest component controls freshness.
        LocalDate observed = newest.isAfter(asOf) ? newest : oldest;
        boolean sameSource = rows.stream().allMatch(row -> row.source().equals(facts.source()) && row.sourceVersion().equals(facts.sourceVersion()));
        SourceEvidence source = sameSource ? new SourceEvidence(facts.source(), facts.sourceVersion(), observed) : null;
        return new Data(facts.registeredOn(), facts.accountOpenedOn(), facts.directorChangedOn(), facts.soleFounderChangedOn(),
                facts.managementHistoryComplete(), facts.internalMonthlyDebtRub(), flows, source);
    }
}
