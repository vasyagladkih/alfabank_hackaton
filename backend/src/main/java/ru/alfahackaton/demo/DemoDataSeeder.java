package ru.alfahackaton.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.alfahackaton.persistance.repository.EvidenceRepository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Opt-in local fixtures only. Re-running never resets applications or rewrites evidence. */
@Component
@ConditionalOnProperty(name = "demo.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;
    private final EvidenceRepository evidence;
    private final TransactionTemplate transactions;

    public DemoDataSeeder(JdbcClient jdbc, EvidenceRepository evidence, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.evidence = evidence;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) { seed(); }

    public int seed() {
        DemoSeedCatalog catalog = DemoSeedCatalog.load();
        transactions.executeWithoutResult(tx -> catalog.profileScenarios().forEach((owner, code) -> {
            DemoScenario scenario = catalog.scenarios().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow();
            seedProfile(userId(owner), inputs(scenario));
        }));
        int inserted = 0;
        for (int index = 0; index < catalog.scenarios().size(); index++) {
            DemoScenario scenario = catalog.scenarios().get(index);
            OffsetDateTime createdAt = catalog.asOf().atTime(12, 0).atOffset(ZoneOffset.UTC).plusSeconds(index);
            Boolean wasInserted = transactions.execute(tx -> {
                long owner = userId(scenario.owner());
                var id = jdbc.sql("""
                        INSERT INTO applications(user_id,amount,term_months,purpose,status,demo_scenario_key,created_at,updated_at)
                        VALUES (:owner,:amount,:months,:purpose,'PROCESSING',:key,:at,:at)
                        ON CONFLICT (demo_scenario_key) WHERE demo_scenario_key IS NOT NULL DO NOTHING RETURNING id
                        """).param("owner", owner).param("amount", scenario.amountRub()).param("months", scenario.termMonths())
                        .param("purpose", scenario.purpose()).param("key", "demo-v1:" + scenario.code()).param("at", createdAt)
                        .query(Long.class).optional();
                if (id.isEmpty()) return false;
                evidence.insertSnapshot(id.get(), catalog.schemaVersion(), catalog.asOf(), createdAt,
                        "DEMO_SCENARIO", scenario.snapshotJson());
                evidence.appendAudit(id.get(), "CREATED", "DEMO_SCENARIO_CREATED", "SYSTEM", null,
                        null, "PROCESSING", 0, "Синтетический сценарий: " + scenario.title(),
                        "{\"demoOnly\":true,\"scenarioCode\":\"" + scenario.code() + "\"}", createdAt);
                jdbc.sql("INSERT INTO processing_jobs(application_id,available_at) VALUES (:id,:at)")
                        .param("id", id.get()).param("at", createdAt).update();
                return true;
            });
            if (Boolean.TRUE.equals(wasInserted)) inserted++;
        }
        return inserted;
    }

    private long userId(String username) {
        return jdbc.sql("SELECT id FROM users WHERE username=:name").param("name", username).query(Long.class).single();
    }

    private static JsonNode inputs(DemoScenario scenario) {
        try { return JSON.readTree(scenario.snapshotJson()).required("inputs"); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid synthetic profile input", e); }
    }

    private void seedProfile(long userId, JsonNode input) {
        JsonNode internal = input.required("internalSource");
        jdbc.sql("""
                INSERT INTO company_profiles(user_id,business_registered_on,account_opened_on,director_changed_on,
                    sole_founder_changed_on,management_history_complete,internal_monthly_debt_rub,source,source_version,as_of)
                VALUES (:id,:registered,:opened,:director,:founder,:complete,0,:source,:sourceVersion,:asOf)
                ON CONFLICT (user_id) DO NOTHING
                """).param("id", userId).param("registered", date(input, "registeredOn"), Types.DATE)
                .param("opened", date(input, "accountOpenedOn"), Types.DATE)
                .param("director", date(input, "directorChangedOn"), Types.DATE)
                .param("founder", date(input, "soleFounderChangedOn"), Types.DATE)
                .param("complete", bool(input, "managementHistoryComplete"), Types.BOOLEAN)
                .param("source", internal.required("source").asText()).param("sourceVersion", internal.required("version").asText())
                .param("asOf", date(internal, "observedOn")).update();
        for (JsonNode flow : input.required("cashflows")) {
            jdbc.sql("""
                    INSERT INTO monthly_cashflows(user_id,month,turnover_rub,source,source_version,as_of)
                    VALUES (:id,:month,:turnover,:source,:sourceVersion,:asOf)
                    ON CONFLICT (user_id,month) DO NOTHING
                    """).param("id", userId).param("month", LocalDate.parse(flow.required("month").asText() + "-01"))
                    .param("turnover", decimal(flow, "netCreditTurnoverRub"), Types.NUMERIC)
                    .param("source", internal.required("source").asText()).param("sourceVersion", internal.required("version").asText())
                    .param("asOf", date(internal, "observedOn")).update();
        }
        JsonNode bureau = input.required("bureauSource");
        jdbc.sql("""
                INSERT INTO synthetic_bureau_facts(user_id,score,monthly_debt_rub,max_overdue_days,recent_closed_overdue_days,
                    source,source_version,as_of)
                VALUES (:id,:score,:debt,:overdue,:recent,:source,:sourceVersion,:asOf)
                ON CONFLICT (user_id) DO NOTHING
                """).param("id", userId).param("score", integer(input, "bureauScore"), Types.INTEGER)
                .param("debt", decimal(input, "existingDebtPaymentRub"), Types.NUMERIC)
                .param("overdue", integer(input, "maxOverdueDays24m"), Types.INTEGER)
                .param("recent", integer(input, "recentClosedOverdueDays12m"), Types.INTEGER)
                .param("source", bureau.required("source").asText()).param("sourceVersion", bureau.required("version").asText())
                .param("asOf", date(bureau, "observedOn")).update();
        JsonNode fns = input.required("fnsSource");
        jdbc.sql("""
                INSERT INTO synthetic_registry_facts(user_id,tax_blocked,bankrupt,liquidating,source,source_version,as_of)
                VALUES (:id,:blocked,:bankrupt,:liquidating,:source,:sourceVersion,:asOf)
                ON CONFLICT (user_id) DO NOTHING
                """).param("id", userId).param("blocked", bool(input, "taxBlocked"), Types.BOOLEAN)
                .param("bankrupt", bool(input, "bankrupt"), Types.BOOLEAN).param("liquidating", bool(input, "liquidating"), Types.BOOLEAN)
                .param("source", fns.required("source").asText()).param("sourceVersion", fns.required("version").asText())
                .param("asOf", date(fns, "observedOn")).update();
    }

    private static LocalDate date(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : LocalDate.parse(node.path(field).asText());
    }
    private static BigDecimal decimal(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).decimalValue();
    }
    private static Integer integer(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).intValue();
    }
    private static Boolean bool(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).booleanValue();
    }
}
