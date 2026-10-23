package ru.alfahackaton.persistance.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.persistance.entity.DemoBureauFacts;
import ru.alfahackaton.persistance.entity.DemoCompanyProfile;
import ru.alfahackaton.persistance.entity.DemoMonthlyCashflow;
import ru.alfahackaton.persistance.entity.DemoRegistryFacts;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read model behind the demo data providers. No real bank or external service is queried. */
@Repository
public class SyntheticDataRepository {
    private final JdbcClient jdbc;

    public SyntheticDataRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<DemoCompanyProfile> findProfile(long userId) {
        return jdbc.sql("SELECT * FROM company_profiles WHERE user_id=:id").param("id", userId)
                .query((rs, row) -> new DemoCompanyProfile(rs.getLong("user_id"),
                        rs.getObject("business_registered_on", LocalDate.class), rs.getObject("account_opened_on", LocalDate.class),
                        rs.getObject("director_changed_on", LocalDate.class), rs.getObject("sole_founder_changed_on", LocalDate.class),
                        rs.getObject("management_history_complete", Boolean.class), rs.getBigDecimal("internal_monthly_debt_rub"),
                        rs.getString("source"), rs.getString("source_version"), rs.getObject("as_of", LocalDate.class))).optional();
    }

    public List<DemoMonthlyCashflow> findCashflows(long userId) {
        return jdbc.sql("SELECT * FROM monthly_cashflows WHERE user_id=:id ORDER BY month").param("id", userId)
                .query((rs, row) -> new DemoMonthlyCashflow(rs.getLong("user_id"), rs.getObject("month", LocalDate.class),
                        rs.getBigDecimal("turnover_rub"), rs.getString("source"), rs.getString("source_version"),
                        rs.getObject("as_of", LocalDate.class))).list();
    }

    public Optional<DemoBureauFacts> findBureauFacts(long userId) {
        return jdbc.sql("SELECT * FROM synthetic_bureau_facts WHERE user_id=:id").param("id", userId)
                .query((rs, row) -> new DemoBureauFacts(rs.getLong("user_id"), rs.getObject("score", Integer.class),
                        rs.getBigDecimal("monthly_debt_rub"), rs.getObject("max_overdue_days", Integer.class),
                        rs.getObject("recent_closed_overdue_days", Integer.class), rs.getString("source"),
                        rs.getString("source_version"), rs.getObject("as_of", LocalDate.class))).optional();
    }

    public Optional<DemoRegistryFacts> findRegistryFacts(long userId) {
        return jdbc.sql("SELECT * FROM synthetic_registry_facts WHERE user_id=:id").param("id", userId)
                .query((rs, row) -> new DemoRegistryFacts(rs.getLong("user_id"), rs.getObject("tax_blocked", Boolean.class),
                        rs.getObject("bankrupt", Boolean.class), rs.getObject("liquidating", Boolean.class),
                        rs.getString("source"), rs.getString("source_version"), rs.getObject("as_of", LocalDate.class))).optional();
    }
}
