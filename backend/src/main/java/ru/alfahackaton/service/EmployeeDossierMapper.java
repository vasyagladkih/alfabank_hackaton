package ru.alfahackaton.service;

import org.springframework.stereotype.Component;
import ru.alfahackaton.dto.*;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.AuditRecord;
import ru.alfahackaton.persistance.entity.SnapshotDocument;
import ru.alfahackaton.risk.Assessment;
import ru.alfahackaton.risk.DemoRiskPolicy;
import ru.alfahackaton.risk.RiskInputs;
import ru.alfahackaton.risk.SourceEvidence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Employee-only projection of persisted evidence. This mapper never gathers or scores new inputs. */
@Component
public class EmployeeDossierMapper {
    private final ApplicationMapper applications;
    private final DemoRiskPolicy policy;

    public EmployeeDossierMapper(ApplicationMapper applications, DemoRiskPolicy policy) {
        this.applications = applications;
        this.policy = policy;
    }

    public EmployeeDossier toDto(ApplicationEntity application, SnapshotDocument document, ScoringSnapshot snapshot,
                                 Assessment assessment, List<EmployeeDecisionType> allowedActions, List<AuditRecord> audit) {
        var dto = new EmployeeDossier();
        dto.setApplication(applications.toDto(application));
        dto.setDemoOnly(true);
        dto.setAllowedActions(allowedActions);
        dto.setAudit(audit.stream().map(this::audit).toList());
        dto.setFacts(List.of());
        dto.setCashflows(List.of());
        RiskInputs inputs = snapshot == null ? null : snapshot.inputs();
        LocalDate asOf = inputs == null ? null : inputs.asOf();
        dto.setSources(List.of(
                source(inputs == null ? null : inputs.internalSource(), "SYNTHETIC_INTERNAL", "Внутренние финансы — демо", asOf, policy.freshness().internalDays()),
                source(inputs == null ? null : inputs.bureauSource(), "SYNTHETIC_BKI", "БКИ — демо", asOf, policy.freshness().bureauDays()),
                source(inputs == null ? null : inputs.fnsSource(), "SYNTHETIC_FNS", "ФНС — демо", asOf, policy.freshness().fnsDays())));
        if (document != null) {
            dto.setSnapshotCapturedAt(document.capturedAt());
            dto.setSnapshotAsOf(document.asOf());
            dto.setSnapshotOrigin(document.origin());
            dto.setSnapshotSha256(document.payloadSha256());
        }
        if (inputs != null) {
            dto.setFacts(facts(inputs));
            if (inputs.cashflows() != null) dto.setCashflows(inputs.cashflows().stream().filter(row -> row != null && row.month() != null)
                    .map(row -> new MonthlyTurnover(row.month().toString()).turnover(decimal(row.netCreditTurnoverRub()))).toList());
        }
        boolean unreadable = document != null && (inputs == null || inputs.asOf() == null)
                || assessment != null && assessment.asOf() == null;
        if (unreadable) {
            dto.getSources().forEach(source -> source.setFreshness(DossierSource.FreshnessEnum.INVALID));
            var facts = new ArrayList<>(dto.getFacts());
            facts.add(fact("snapshotReadError", "Доступность входных данных",
                    "Сохранённый снимок не читается или не содержит даты оценки; одобрение и предложение недоступны.", null, "SNAPSHOT"));
            dto.setFacts(facts);
        }
        // The API requires a valuation date for an assessment. Preserve, but do not invent, missing historical data.
        if (assessment != null && assessment.asOf() != null) dto.setAssessment(assessment(assessment));
        return dto;
    }

    private List<DossierFact> facts(RiskInputs input) {
        return List.of(
                fact("registeredOn", "Дата регистрации", input.registeredOn(), null, "SYNTHETIC_INTERNAL"),
                fact("accountOpenedOn", "Дата открытия счёта", input.accountOpenedOn(), null, "SYNTHETIC_INTERNAL"),
                fact("directorChangedOn", "Последняя смена директора", managementChange(input.directorChangedOn(), input.managementHistoryComplete()), null, "SYNTHETIC_INTERNAL"),
                fact("soleFounderChangedOn", "Последняя смена единственного учредителя", managementChange(input.soleFounderChangedOn(), input.managementHistoryComplete()), null, "SYNTHETIC_INTERNAL"),
                fact("managementHistoryComplete", "Полнота истории руководства", yesNo(input.managementHistoryComplete()), null, "SYNTHETIC_INTERNAL"),
                fact("existingDebtPaymentRub", "Совокупный платёж по существующим долгам", decimal(input.existingDebtPaymentRub()), "RUB/месяц", "SYNTHETIC_INTERNAL + SYNTHETIC_BKI"),
                fact("bureauScore", "Синтетический балл БКИ", input.bureauScore(), "баллы", "SYNTHETIC_BKI"),
                fact("maxOverdueDays24m", "Максимальная просрочка за 24 месяца", input.maxOverdueDays24m(), "дни", "SYNTHETIC_BKI"),
                fact("recentClosedOverdueDays12m", "Закрытая просрочка за 12 месяцев", input.recentClosedOverdueDays12m(), "дни", "SYNTHETIC_BKI"),
                fact("taxBlocked", "Блокировка счёта", yesNo(input.taxBlocked()), null, "SYNTHETIC_FNS"),
                fact("bankrupt", "Банкротство", yesNo(input.bankrupt()), null, "SYNTHETIC_FNS"),
                fact("liquidating", "Ликвидация", yesNo(input.liquidating()), null, "SYNTHETIC_FNS"));
    }

    private static DossierFact fact(String code, String label, Object value, String unit, String source) {
        return new DossierFact(code, label, source).value(value == null ? null : value.toString()).unit(unit);
    }
    private static String yesNo(Boolean value) { return value == null ? null : value ? "Да" : "Нет"; }
    private static String managementChange(LocalDate date, Boolean complete) {
        return date != null ? date.toString() : Boolean.TRUE.equals(complete) ? "Изменений не было" : null;
    }
    private static String decimal(BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }

    private DossierSource source(SourceEvidence evidence, String expected, String label, LocalDate asOf, int maximumAge) {
        var dto = new DossierSource();
        dto.setCode(expected);
        dto.setLabel(label);
        dto.setDemoOnly(true);
        if (evidence != null) {
            dto.setVersion(evidence.version());
            dto.setObservedOn(evidence.observedOn());
        }
        if (evidence == null || evidence.source() == null || evidence.version() == null || evidence.version().isBlank() || evidence.observedOn() == null) {
            dto.setFreshness(DossierSource.FreshnessEnum.MISSING);
        } else if (asOf == null || !expected.equals(evidence.source()) || evidence.observedOn().isAfter(asOf)) {
            dto.setFreshness(DossierSource.FreshnessEnum.INVALID);
        } else if (evidence.observedOn().isBefore(asOf.minusDays(maximumAge))) {
            dto.setFreshness(DossierSource.FreshnessEnum.STALE);
        } else dto.setFreshness(DossierSource.FreshnessEnum.FRESH);
        return dto;
    }

    private RiskAssessment assessment(Assessment stored) {
        var dto = new RiskAssessment();
        dto.setRuleVersion(stored.ruleVersion());
        dto.setAsOf(stored.asOf());
        dto.setOutcome(ApplicationStatus.fromValue(stored.outcome().name()));
        dto.setScore(stored.score());
        dto.setMaxScore(850);
        dto.setEvidenceComplete(stored.evidenceComplete());
        dto.setEligibleForManualApproval(stored.eligibleForManualApproval());
        dto.setAnnualRate(decimal(stored.annualRatePercent()));
        dto.setMonthlyIncome(decimal(stored.monthlyIncomeRub()));
        dto.setExistingDebtPayment(decimal(stored.existingDebtPaymentRub()));
        dto.setRequestedPayment(decimal(stored.requestedPaymentRub()));
        dto.setDebtRatio(decimal(stored.requestedDebtRatio()));
        dto.setAffordableAmount(stored.maxAffordableAmountRub() == null ? null : stored.maxAffordableAmountRub().longValueExact());
        dto.setComponents(stored.scoreFactors().stream().map(factor -> new ScoreContribution(factor.code(), factor.label(),
                decimal(factor.value()), factor.points(), factor.maximum(), factor.explanation())).toList());
        dto.setFindings(stored.reasons().stream().map(reason -> new RuleFinding(reason.code(),
                RuleFinding.SeverityEnum.fromValue(reason.severity().name()), reason.message())).toList());
        return dto;
    }

    private AuditEvent audit(AuditRecord event) {
        var dto = new AuditEvent();
        dto.setId(event.id());
        dto.setEventType(event.eventType());
        dto.setActorRole(AuditEvent.ActorRoleEnum.fromValue(event.actorType()));
        dto.setFromStatus(event.fromStatus() == null ? null : ApplicationStatus.fromValue(event.fromStatus()));
        dto.setToStatus(ApplicationStatus.fromValue(event.toStatus()));
        dto.setRationale(event.rationale());
        dto.setCreatedAt(event.createdAt());
        dto.setVersion(event.applicationVersion());
        return dto;
    }
}
