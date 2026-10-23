package ru.alfahackaton.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import ru.alfahackaton.demo.DemoDataSeeder;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.EvidenceRepository;
import ru.alfahackaton.persistance.repository.ProcessingJobRepository;
import ru.alfahackaton.persistance.repository.SyntheticDataRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.provider.mock.MockBkiDataProvider;
import ru.alfahackaton.provider.mock.MockFnsDataProvider;
import ru.alfahackaton.provider.mock.MockInternalFinanceDataProvider;
import ru.alfahackaton.risk.DemoRiskPolicy;
import ru.alfahackaton.risk.RiskEngine;
import ru.alfahackaton.support.PostgresTestDatabase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class WorkflowTestSupport {
    public final PostgresTestDatabase db;
    public final JdbcClient jdbc;
    public final DataSourceTransactionManager transactions;
    public final ApplicationRepository applications;
    public final EvidenceRepository evidence;
    public final ProcessingJobRepository jobs;
    public final UserRepository users;
    public final SnapshotService snapshots;
    public final RiskEngine engine = new RiskEngine(DemoRiskPolicy.defaults());
    public final Clock clock = Clock.fixed(Instant.parse("2026-10-23T13:00:00Z"), ZoneOffset.UTC);
    public final ApplicationMapper mapper = new ApplicationMapper();
    public final ApplicationService service;

    public WorkflowTestSupport(PostgresTestDatabase db) {
        this.db = db;
        jdbc = JdbcClient.create(db.dataSource());
        transactions = new DataSourceTransactionManager(db.dataSource());
        applications = new ApplicationRepository(jdbc);
        evidence = new EvidenceRepository(jdbc);
        jobs = new ProcessingJobRepository(jdbc);
        users = new UserRepository(jdbc);
        var sources = new SyntheticDataRepository(jdbc);
        snapshots = new SnapshotService(new MockInternalFinanceDataProvider(sources), new MockBkiDataProvider(sources),
                new MockFnsDataProvider(sources), users, evidence, engine.policy(), clock, "2026-10-23");
        service = new ApplicationService(applications, users, snapshots, jobs, evidence, mapper, transactions, clock);
    }

    public int seed() { return new DemoDataSeeder(jdbc, evidence, transactions).seed(); }
    public ScoringProcessor processor() {
        return new ScoringProcessor(applications, jobs, evidence, snapshots, engine, transactions, clock, 3, 1000);
    }
    public long owner(String name) { return users.findByUsername(name).orElseThrow().id(); }
}
