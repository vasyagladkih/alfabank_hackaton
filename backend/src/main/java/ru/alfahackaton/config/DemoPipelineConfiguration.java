package ru.alfahackaton.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.alfahackaton.persistance.repository.SyntheticDataRepository;
import ru.alfahackaton.provider.BkiDataProvider;
import ru.alfahackaton.provider.FnsDataProvider;
import ru.alfahackaton.provider.InternalFinanceDataProvider;
import ru.alfahackaton.provider.mock.MockBkiDataProvider;
import ru.alfahackaton.provider.mock.MockFnsDataProvider;
import ru.alfahackaton.provider.mock.MockInternalFinanceDataProvider;
import ru.alfahackaton.risk.DemoRiskPolicy;
import ru.alfahackaton.risk.RiskEngine;

import java.time.Clock;

/** The only shipped providers are deterministic mocks. Replace an adapter here when extending the prototype. */
@Configuration
@EnableScheduling
public class DemoPipelineConfiguration {
    @Bean public Clock applicationClock() { return Clock.systemUTC(); }
    @Bean public DemoRiskPolicy demoRiskPolicy() { return DemoRiskPolicy.defaults(); }
    @Bean public RiskEngine riskEngine(DemoRiskPolicy policy) { return new RiskEngine(policy); }
    @Bean
    @ConditionalOnProperty(name="demo.data-provider", havingValue="mock", matchIfMissing=true)
    public InternalFinanceDataProvider internalFinanceDataProvider(SyntheticDataRepository data) { return new MockInternalFinanceDataProvider(data); }
    @Bean
    @ConditionalOnProperty(name="demo.data-provider", havingValue="mock", matchIfMissing=true)
    public BkiDataProvider bkiDataProvider(SyntheticDataRepository data) { return new MockBkiDataProvider(data); }
    @Bean
    @ConditionalOnProperty(name="demo.data-provider", havingValue="mock", matchIfMissing=true)
    public FnsDataProvider fnsDataProvider(SyntheticDataRepository data) { return new MockFnsDataProvider(data); }
}
