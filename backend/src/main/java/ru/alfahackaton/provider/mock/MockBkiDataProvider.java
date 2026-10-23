package ru.alfahackaton.provider.mock;

import ru.alfahackaton.persistance.repository.SyntheticDataRepository;
import ru.alfahackaton.provider.BkiDataProvider;
import ru.alfahackaton.risk.SourceEvidence;
import java.time.LocalDate;

/** Local synthetic report; no credit-bureau request is made. */
public final class MockBkiDataProvider implements BkiDataProvider {
    private final SyntheticDataRepository repository;
    public MockBkiDataProvider(SyntheticDataRepository repository) { this.repository = repository; }
    @Override
    public Data fetch(long userId, LocalDate asOf) {
        return repository.findBureauFacts(userId).map(facts -> new Data(facts.score(), facts.monthlyDebtRub(),
                facts.maxOverdueDays(), facts.recentClosedOverdueDays(),
                new SourceEvidence(facts.source(), facts.sourceVersion(), facts.asOf())))
                .orElseGet(() -> new Data(null, null, null, null, null));
    }
}
