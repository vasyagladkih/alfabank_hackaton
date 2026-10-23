package ru.alfahackaton.provider.mock;

import ru.alfahackaton.persistance.repository.SyntheticDataRepository;
import ru.alfahackaton.provider.FnsDataProvider;
import ru.alfahackaton.risk.SourceEvidence;
import java.time.LocalDate;

/** Local synthetic registry facts; no tax or company-register request is made. */
public final class MockFnsDataProvider implements FnsDataProvider {
    private final SyntheticDataRepository repository;
    public MockFnsDataProvider(SyntheticDataRepository repository) { this.repository = repository; }
    @Override
    public Data fetch(long userId, LocalDate asOf) {
        return repository.findRegistryFacts(userId).map(facts -> new Data(facts.taxBlocked(), facts.bankrupt(), facts.liquidating(),
                new SourceEvidence(facts.source(), facts.sourceVersion(), facts.asOf())))
                .orElseGet(() -> new Data(null, null, null, null));
    }
}
