package ru.alfahackaton.persistance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.alfahackaton.persistance.repository.SyntheticDataRepository;
import ru.alfahackaton.support.PostgresTestDatabase;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class SyntheticDataRepositoryTest {
    @Test
    void nullableFlagsAndDebtRemainUnknownAndDecimalTurnoverIsExact() {
        try (var db = new PostgresTestDatabase()) {
            db.migrate();
            long owner = db.jdbc().queryForObject("SELECT id FROM users WHERE username='client_good'", Long.class);
            db.jdbc().update("""
                    INSERT INTO synthetic_registry_facts(user_id,tax_blocked,bankrupt,liquidating,source,as_of)
                    VALUES (?,NULL,FALSE,TRUE,'SYNTHETIC_FNS',DATE '2026-10-23')
                    """, owner);
            db.jdbc().update("""
                    INSERT INTO synthetic_bureau_facts(user_id,score,monthly_debt_rub,source,as_of)
                    VALUES (?,700,NULL,'SYNTHETIC_BKI',DATE '2026-10-23')
                    """, owner);
            db.jdbc().update("""
                    INSERT INTO monthly_cashflows(user_id,month,turnover_rub,source,as_of)
                    VALUES (?,DATE '2026-09-01',1234567.89,'SYNTHETIC_INTERNAL',DATE '2026-10-23')
                    """, owner);
            var repository = new SyntheticDataRepository(JdbcClient.create(db.dataSource()));
            assertNull(repository.findRegistryFacts(owner).orElseThrow().taxBlocked());
            assertFalse(repository.findRegistryFacts(owner).orElseThrow().bankrupt());
            assertTrue(repository.findRegistryFacts(owner).orElseThrow().liquidating());
            assertNull(repository.findBureauFacts(owner).orElseThrow().monthlyDebtRub());
            assertEquals(new BigDecimal("1234567.89"), repository.findCashflows(owner).getFirst().turnoverRub());
            assertTrue(repository.findProfile(owner).isEmpty());
        }
    }
}
