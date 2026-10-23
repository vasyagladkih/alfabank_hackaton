package ru.alfahackaton.risk;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure, standard-Java demonstration rules. No persistence, network, wall clock or ML dependency. */
public final class RiskEngine {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private static final BigDecimal SIX = new BigDecimal("6");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final BigDecimal RATE_DIVISOR = new BigDecimal("1200");
    private static final BigDecimal MAXIMUM_RATE = new BigDecimal("100");
    private final DemoRiskPolicy policy;

    public RiskEngine(DemoRiskPolicy policy) { this.policy = Objects.requireNonNull(policy); }
    public DemoRiskPolicy policy() { return policy; }
    public Assessment assess(RiskInputs inputs, LoanRequest request) {
        validateRequest(request);
        if (inputs == null || inputs.asOf() == null) {
            return stopped(inputs, Outcome.MANUAL_REVIEW, false,
                    List.of(reason("SNAPSHOT_DATE_MISSING", Severity.DATA_QUALITY, "Нет фиксированной даты входных данных демонстрации.")));
        }

        List<RiskReason> quality = qualityProblems(inputs);
        List<RiskReason> hard = hardStops(inputs);
        if (!hard.isEmpty()) {
            hard.addAll(quality);
            return stopped(inputs, Outcome.REJECTED, quality.isEmpty(), hard);
        }
        if (!quality.isEmpty()) return stopped(inputs, Outcome.MANUAL_REVIEW, false, quality);

        List<MonthlyCashflow> months = orderedMonths(inputs);
        BigDecimal sumSix = sum(months.subList(6, 12));
        BigDecimal incomeNumerator = sumSix.multiply(policy.affordability().incomeFraction());
        if (incomeNumerator.signum() == 0) {
            return stopped(inputs, Outcome.MANUAL_REVIEW, false,
                    List.of(reason("ZERO_INCOME", Severity.DATA_QUALITY,
                            "Оборот за шесть месяцев равен нулю: доля платежей и стабильность не рассчитываются; требуется рассмотрение.")));
        }

        List<ScoreFactor> factors = scoreFactors(inputs, months, sumSix);
        int score = factors.stream().mapToInt(ScoreFactor::points).sum();
        ScoreBand band = scoreBand(score);
        BigDecimal rate = band == ScoreBand.GREEN ? policy.affordability().greenAnnualRatePercent() : policy.affordability().manualAnnualRatePercent();
        BigDecimal income = incomeNumerator.divide(SIX, MATH);
        BigDecimal requestedPayment = monthlyPayment(new LoanTerms(request.amountRub(), request.termMonths(), rate));
        BigDecimal totalPayments = inputs.existingDebtPaymentRub().add(requestedPayment);
        BigDecimal ratio = debtRatio(totalPayments, incomeNumerator);
        BigDecimal cap = affordableAmount(inputs.existingDebtPaymentRub(), incomeNumerator, request, rate);
        List<RiskReason> reasons = softStops(inputs, months, totalPayments, incomeNumerator);
        boolean affordable = withinRatio(totalPayments, incomeNumerator, policy.affordability().maximumDebtRatio());
        boolean eligible = band != ScoreBand.RED && cap.compareTo(policy.product().minimumAmountRub()) >= 0;
        Outcome outcome;
        LoanTerms offer = null;

        if (band == ScoreBand.RED) {
            outcome = Outcome.REJECTED;
            reasons.add(reason("LOW_SCORE", Severity.DECISION,
                    "Демонстрационный балл " + score + " ниже " + policy.scoring().manualMinimum() + "."));
        } else if (band == ScoreBand.GRAY || !reasons.isEmpty()) {
            outcome = Outcome.MANUAL_REVIEW;
            if (band == ScoreBand.GRAY) reasons.add(reason("GRAY_SCORE", Severity.WARNING,
                    "Демонстрационный балл " + score + " требует объяснённого решения сотрудника."));
            if (!eligible) reasons.add(reason("NO_AFFORDABLE_OFFER", Severity.WARNING,
                    "Доступная сумма меньше минимальной суммы демонстрационного продукта."));
        } else if (affordable) {
            outcome = Outcome.APPROVED;
            reasons.add(reason("DEMO_AUTO_APPROVAL", Severity.DECISION,
                    "Синтетический пример прошёл правила демонстрации; это не реальное кредитное решение."));
        } else if (eligible) {
            outcome = Outcome.OFFER_PROPOSED;
            offer = new LoanTerms(cap, request.termMonths(), rate);
            reasons.add(reason("AFFORDABILITY_COUNTEROFFER", Severity.DECISION,
                    "Запрошенная сумма превышает демо-лимит платежей; предложена меньшая сумма с теми же сроком и ставкой."));
        } else {
            outcome = Outcome.REJECTED;
            reasons.add(reason("NO_AFFORDABLE_OFFER", Severity.DECISION,
                    "По демонстрационным допущениям нет доступного предложения от " + policy.product().minimumAmountRub().toPlainString() + " ₽."));
        }
        return new Assessment(policy.version(), inputs.asOf(), outcome, score, band, factors, reasons,
                true, eligible, rate, income, inputs.existingDebtPaymentRub(), requestedPayment, ratio, cap, offer);
    }

    public ScoreBand scoreBand(int score) {
        if (score < 0 || score > 850) throw new IllegalArgumentException("Demo score must be between 0 and 850");
        if (score >= policy.scoring().greenMinimum()) return ScoreBand.GREEN;
        return score >= policy.scoring().manualMinimum() ? ScoreBand.GRAY : ScoreBand.RED;
    }

    /** Re-evaluate against the same immutable snapshot before any employee approval or counteroffer. */
    public TermsEvaluation evaluateTerms(RiskInputs inputs, LoanRequest request, LoanTerms terms) {
        Assessment assessment = assess(inputs, request);
        List<RiskReason> problems = new ArrayList<>();
        if (!assessment.eligibleForManualApproval()) {
            problems.add(reason("TERMS_NOT_ELIGIBLE", Severity.DECISION,
                    "Одобрение недоступно: стоп-фактор, неполные данные, низкий балл или недостаточная доступная сумма."));
            problems.addAll(assessment.reasons());
            return new TermsEvaluation(false, problems, null, null, assessment.maxAffordableAmountRub());
        }
        if (terms == null || terms.amountRub() == null || !wholeRubles(terms.amountRub())
                || terms.amountRub().compareTo(policy.product().minimumAmountRub()) < 0
                || terms.amountRub().compareTo(policy.product().maximumAmountRub()) > 0
                || terms.amountRub().compareTo(request.amountRub()) > 0) {
            problems.add(reason("OFFER_AMOUNT_INVALID", Severity.DECISION,
                    "Сумма должна быть в целых рублях, в границах продукта и не больше запрошенной."));
        }
        if (terms == null || terms.termMonths() != request.termMonths()) {
            problems.add(reason("TERMS_TERM_CHANGED", Severity.DECISION, "В этой демонстрации срок предложения совпадает с запрошенным."));
        }
        if (terms == null || terms.annualRatePercent() == null || terms.annualRatePercent().compareTo(assessment.annualRatePercent()) != 0) {
            problems.add(reason("TERMS_RATE_CHANGED", Severity.DECISION, "В этой демонстрации ставка предложения фиксирована версией правил."));
        }
        if (!problems.isEmpty()) return new TermsEvaluation(false, problems, null, null, assessment.maxAffordableAmountRub());

        BigDecimal payment = monthlyPayment(terms);
        BigDecimal incomeNumerator = sum(orderedMonths(inputs).subList(6, 12)).multiply(policy.affordability().incomeFraction());
        BigDecimal total = inputs.existingDebtPaymentRub().add(payment);
        if (terms.amountRub().compareTo(assessment.maxAffordableAmountRub()) > 0
                || !withinRatio(total, incomeNumerator, policy.affordability().maximumDebtRatio())) {
            problems.add(reason("OFFER_EXCEEDS_CAP", Severity.DECISION,
                    "Предложение превышает доступный лимит после округления ежемесячного платежа."));
        }
        return new TermsEvaluation(problems.isEmpty(), problems, payment, debtRatio(total, incomeNumerator), assessment.maxAffordableAmountRub());
    }

    /** Conservative payment rounded up to the next kopek, with a separate zero-rate branch. */
    public static BigDecimal monthlyPayment(LoanTerms terms) {
        if (terms == null || terms.amountRub() == null || terms.amountRub().signum() < 0
                || terms.termMonths() < 1 || terms.termMonths() > 60 || !validRate(terms.annualRatePercent())) {
            throw new IllegalArgumentException("Invalid demonstration loan terms");
        }
        if (terms.annualRatePercent().signum() == 0) {
            return terms.amountRub().divide(BigDecimal.valueOf(terms.termMonths()), 2, RoundingMode.CEILING);
        }
        BigDecimal monthlyRate = terms.annualRatePercent().divide(RATE_DIVISOR, MATH);
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate, MATH).pow(terms.termMonths(), MATH);
        return terms.amountRub().multiply(monthlyRate, MATH).multiply(growth, MATH)
                .divide(growth.subtract(BigDecimal.ONE), MATH).setScale(2, RoundingMode.CEILING);
    }

    private void validateRequest(LoanRequest request) {
        if (request == null || request.amountRub() == null || !wholeRubles(request.amountRub())
                || request.amountRub().compareTo(policy.product().minimumAmountRub()) < 0
                || request.amountRub().compareTo(policy.product().maximumAmountRub()) > 0
                || request.termMonths() < policy.product().minimumTermMonths() || request.termMonths() > policy.product().maximumTermMonths()) {
            throw new IllegalArgumentException("Request must satisfy the configured demo amount and term bounds");
        }
    }

    private List<RiskReason> hardStops(RiskInputs inputs) {
        List<RiskReason> reasons = new ArrayList<>();
        if (sourceFresh(inputs.fnsSource(), "SYNTHETIC_FNS", inputs.asOf(), policy.freshness().fnsDays())) {
            if (Boolean.TRUE.equals(inputs.taxBlocked())) reasons.add(reason("TAX_BLOCK", Severity.HARD_STOP, "В синтетической справке отмечена блокировка счёта."));
            if (Boolean.TRUE.equals(inputs.bankrupt())) reasons.add(reason("BANKRUPTCY", Severity.HARD_STOP, "В синтетической справке отмечено банкротство."));
            if (Boolean.TRUE.equals(inputs.liquidating())) reasons.add(reason("LIQUIDATION", Severity.HARD_STOP, "В синтетической справке отмечена ликвидация."));
        }
        if (sourceFresh(inputs.bureauSource(), "SYNTHETIC_BKI", inputs.asOf(), policy.freshness().bureauDays())
                && inputs.maxOverdueDays24m() != null && inputs.maxOverdueDays24m() > policy.stops().hardOverdueDays()) {
            reasons.add(reason("SEVERE_OVERDUE", Severity.HARD_STOP, "Синтетическая просрочка за 24 месяца превышает " + policy.stops().hardOverdueDays() + " дней."));
        }
        if (sourceFresh(inputs.internalSource(), "SYNTHETIC_INTERNAL", inputs.asOf(), policy.freshness().internalDays())
                && validPastDate(inputs.registeredOn(), inputs.asOf())
                && inputs.registeredOn().plusMonths(policy.stops().minimumBusinessMonths()).isAfter(inputs.asOf())) {
            reasons.add(reason("BUSINESS_TOO_YOUNG", Severity.HARD_STOP, "С регистрации прошло меньше " + policy.stops().minimumBusinessMonths() + " календарных месяцев."));
        }
        return reasons;
    }

    private List<RiskReason> qualityProblems(RiskInputs inputs) {
        List<RiskReason> problems = new ArrayList<>();
        checkSource(problems, inputs.internalSource(), "SYNTHETIC_INTERNAL", "INTERNAL", inputs.asOf(), policy.freshness().internalDays());
        checkSource(problems, inputs.bureauSource(), "SYNTHETIC_BKI", "BUREAU", inputs.asOf(), policy.freshness().bureauDays());
        checkSource(problems, inputs.fnsSource(), "SYNTHETIC_FNS", "FNS", inputs.asOf(), policy.freshness().fnsDays());
        if (!validPastDate(inputs.registeredOn(), inputs.asOf())) problems.add(reason("REGISTRATION_DATE_INVALID", Severity.DATA_QUALITY, "Дата регистрации отсутствует или находится в будущем."));
        if (!validPastDate(inputs.accountOpenedOn(), inputs.asOf())) problems.add(reason("ACCOUNT_DATE_INVALID", Severity.DATA_QUALITY, "Дата открытия счёта отсутствует или находится в будущем."));
        if (!Boolean.TRUE.equals(inputs.managementHistoryComplete())) problems.add(reason("MANAGEMENT_HISTORY_MISSING", Severity.DATA_QUALITY, "История смены директора и единственного учредителя не подтверждена синтетическим источником."));
        if ((inputs.directorChangedOn() != null && inputs.directorChangedOn().isAfter(inputs.asOf()))
                || (inputs.soleFounderChangedOn() != null && inputs.soleFounderChangedOn().isAfter(inputs.asOf()))) {
            problems.add(reason("MANAGEMENT_DATE_INVALID", Severity.DATA_QUALITY, "Дата смены руководства находится в будущем."));
        }
        if (inputs.existingDebtPaymentRub() == null || !validMoney(inputs.existingDebtPaymentRub()) || inputs.existingDebtPaymentRub().signum() < 0) {
            problems.add(reason("DEBT_INVALID", Severity.DATA_QUALITY, "Совокупный платёж по существующим долгам отсутствует или некорректен."));
        }
        if (inputs.bureauScore() == null) problems.add(reason("BUREAU_SCORE_MISSING", Severity.DATA_QUALITY, "В синтетическом отчёте отсутствует балл БКИ."));
        else if (inputs.bureauScore() < 0 || inputs.bureauScore() > 1000) problems.add(reason("BUREAU_SCORE_INVALID", Severity.DATA_QUALITY, "Синтетический балл БКИ должен быть от 0 до 1000."));
        if (inputs.maxOverdueDays24m() == null || inputs.recentClosedOverdueDays12m() == null) {
            problems.add(reason("OVERDUE_HISTORY_MISSING", Severity.DATA_QUALITY, "Не заполнены окна истории просрочек за 24 и 12 месяцев."));
        } else if (inputs.maxOverdueDays24m() < 0 || inputs.recentClosedOverdueDays12m() < 0 || inputs.recentClosedOverdueDays12m() > inputs.maxOverdueDays24m()) {
            problems.add(reason("OVERDUE_HISTORY_INVALID", Severity.DATA_QUALITY, "История просрочек отрицательна или противоречива."));
        }
        if (inputs.taxBlocked() == null || inputs.bankrupt() == null || inputs.liquidating() == null) {
            problems.add(reason("LEGAL_FACTS_MISSING", Severity.DATA_QUALITY, "Отсутствует один из синтетических юридических признаков; неизвестное не считается отсутствием риска."));
        }
        checkCashflows(inputs, problems);
        return problems;
    }

    private void checkCashflows(RiskInputs inputs, List<RiskReason> problems) {
        if (inputs.cashflows() == null || inputs.cashflows().size() != 12) {
            problems.add(reason("CASHFLOW_WINDOW_INCOMPLETE", Severity.DATA_QUALITY, "Нужны все 12 завершённых месяцев оборотов."));
            return;
        }
        YearMonth lastClosed = YearMonth.from(inputs.asOf()).minusMonths(1);
        Set<YearMonth> months = new HashSet<>();
        boolean badWindow = false;
        boolean badValue = false;
        for (MonthlyCashflow cashflow : inputs.cashflows()) {
            if (cashflow == null || cashflow.month() == null) { badWindow = true; continue; }
            if (!months.add(cashflow.month()) || cashflow.month().isAfter(lastClosed) || cashflow.month().isBefore(lastClosed.minusMonths(11))) badWindow = true;
            if (cashflow.netCreditTurnoverRub() == null || !validMoney(cashflow.netCreditTurnoverRub()) || cashflow.netCreditTurnoverRub().signum() < 0) badValue = true;
        }
        if (badWindow || months.size() != 12) problems.add(reason("CASHFLOW_WINDOW_INVALID", Severity.DATA_QUALITY, "Месяцы оборотов должны быть последовательными, закрытыми и без повторов."));
        if (badValue) problems.add(reason("CASHFLOW_VALUE_INVALID", Severity.DATA_QUALITY, "Чистый месячный оборот отсутствует, отрицателен или содержит доли копейки."));
    }

    private List<ScoreFactor> scoreFactors(RiskInputs inputs, List<MonthlyCashflow> months, BigDecimal sumSix) {
        DemoRiskPolicy.Scoring settings = policy.scoring();
        long tenure = ChronoUnit.MONTHS.between(inputs.accountOpenedOn(), inputs.asOf());
        int tenurePoints = tenure > settings.matureAccountMonths() ? 200 : tenure >= settings.establishedAccountMonths() ? 150 : tenure >= settings.minimumAccountMonths() ? 80 : 0;
        BigDecimal average = sumSix.divide(SIX, MATH);
        int turnoverPoints = sumSix.compareTo(settings.highTurnoverRub().multiply(SIX)) > 0 ? 250
                : sumSix.compareTo(settings.mediumTurnoverRub().multiply(SIX)) >= 0 ? 190
                : sumSix.compareTo(settings.lowTurnoverRub().multiply(SIX)) >= 0 ? 120 : 50;
        int bureauPoints = inputs.bureauScore() >= settings.highBureauScore() ? 250 : inputs.bureauScore() >= settings.mediumBureauScore() ? 150 : 40;
        BigDecimal sumSquares = months.subList(6, 12).stream().map(MonthlyCashflow::netCreditTurnoverRub)
                .map(value -> value.multiply(value)).reduce(BigDecimal.ZERO, BigDecimal::add);
        // CV² = (N * sum(x²) - sum(x)²) / sum(x)². Compare squared values exactly at 15/30%.
        BigDecimal meanDenominator = sumSix.multiply(sumSix);
        BigDecimal varianceNumerator = SIX.multiply(sumSquares).subtract(meanDenominator);
        int stabilityPoints = varianceNumerator.compareTo(meanDenominator.multiply(settings.lowCv().pow(2))) < 0 ? 150
                : varianceNumerator.compareTo(meanDenominator.multiply(settings.highCv().pow(2))) <= 0 ? 90 : 30;
        BigDecimal cv = varianceNumerator.divide(meanDenominator, MATH).sqrt(MATH);
        return List.of(
                new ScoreFactor("ACCOUNT_TENURE", "Срок обслуживания", BigDecimal.valueOf(tenure), tenurePoints, 200,
                        "Полные месяцы с открытия счёта: " + tenure + "; баллы по версии " + policy.version() + "."),
                new ScoreFactor("TURNOVER", "Средний оборот", average, turnoverPoints, 250,
                        "Среднее чистых поступлений за последние шесть закрытых месяцев, без внутренних переводов."),
                new ScoreFactor("BUREAU_SCORE", "Синтетический балл БКИ", BigDecimal.valueOf(inputs.bureauScore()), bureauPoints, 250,
                        "Фиксированный учебный балл из mock-провайдера; не вероятность дефолта."),
                new ScoreFactor("TURNOVER_STABILITY", "Стабильность оборота", cv, stabilityPoints, 150,
                        "Коэффициент вариации шести месяцев: стандартное отклонение генеральной совокупности / среднее."));
    }

    private List<RiskReason> softStops(RiskInputs inputs, List<MonthlyCashflow> months, BigDecimal totalPayments, BigDecimal incomeNumerator) {
        List<RiskReason> warnings = new ArrayList<>();
        if (recentChange(inputs.directorChangedOn(), inputs.asOf())) warnings.add(reason("RECENT_DIRECTOR_CHANGE", Severity.WARNING, "Директор сменился менее " + policy.stops().managementChangeDays() + " дней назад."));
        if (recentChange(inputs.soleFounderChangedOn(), inputs.asOf())) warnings.add(reason("RECENT_FOUNDER_CHANGE", Severity.WARNING, "Единственный учредитель сменился менее " + policy.stops().managementChangeDays() + " дней назад."));
        BigDecimal annualSum = sum(months);
        BigDecimal latestTimesTwelve = months.get(11).netCreditTurnoverRub().multiply(TWELVE);
        if (latestTimesTwelve.compareTo(annualSum.multiply(BigDecimal.ONE.subtract(policy.stops().turnoverDropFraction()))) < 0) {
            warnings.add(reason("TURNOVER_DROP", Severity.WARNING,
                    "Оборот последнего закрытого месяца упал больше чем на " + policy.stops().turnoverDropFraction().movePointRight(2).toPlainString() + "% относительно среднего за 12 месяцев."));
        }
        if (inputs.recentClosedOverdueDays12m() > 0 && inputs.recentClosedOverdueDays12m() <= policy.stops().shortOverdueDays()) {
            warnings.add(reason("CLOSED_SHORT_OVERDUE", Severity.WARNING, "За 12 месяцев отмечена закрытая короткая просрочка."));
        }
        if (inputs.maxOverdueDays24m() > policy.stops().shortOverdueDays()) {
            warnings.add(reason("MODERATE_OVERDUE_HISTORY", Severity.WARNING, "Просрочка между короткой и критической границами требует ручной проверки."));
        }
        if (totalPayments.multiply(SIX).compareTo(incomeNumerator.multiply(policy.affordability().borderlineDebtRatio())) >= 0
                && withinRatio(totalPayments, incomeNumerator, policy.affordability().maximumDebtRatio())) {
            warnings.add(reason("BORDERLINE_DEBT_RATIO", Severity.WARNING,
                    "Доля платежей находится в пограничной зоне " + policy.affordability().borderlineDebtRatio().movePointRight(2).toPlainString() + "–"
                            + policy.affordability().maximumDebtRatio().movePointRight(2).toPlainString() + "% включительно."));
        }
        return warnings;
    }

    private BigDecimal affordableAmount(BigDecimal debt, BigDecimal incomeNumerator, LoanRequest request, BigDecimal rate) {
        // A kopek payment must fit the exact budget. Flooring the budget avoids optimistic rounding.
        BigDecimal budget = incomeNumerator.multiply(policy.affordability().maximumDebtRatio()).subtract(debt.multiply(SIX))
                .divide(SIX, 2, RoundingMode.FLOOR);
        if (budget.signum() <= 0) return new BigDecimal("0.00");
        BigDecimal rawCap;
        if (rate.signum() == 0) rawCap = budget.multiply(BigDecimal.valueOf(request.termMonths()));
        else {
            BigDecimal monthlyRate = rate.divide(RATE_DIVISOR, MATH);
            BigDecimal growth = BigDecimal.ONE.add(monthlyRate, MATH).pow(request.termMonths(), MATH);
            rawCap = budget.multiply(growth.subtract(BigDecimal.ONE), MATH).divide(monthlyRate.multiply(growth, MATH), MATH);
        }
        // Existing OpenAPI amounts are int64 RUB. Keep payments in kopeks, amounts in whole RUB.
        BigDecimal cap = rawCap.min(request.amountRub()).min(policy.product().maximumAmountRub()).setScale(0, RoundingMode.FLOOR).setScale(2);
        if (monthlyPayment(new LoanTerms(cap, request.termMonths(), rate)).compareTo(budget) <= 0) return cap;
        // Rare numerical boundary: bounded binary search in whole rubles, never an unbounded decrement loop.
        BigInteger low = BigInteger.ZERO;
        BigInteger high = cap.toBigIntegerExact();
        while (low.compareTo(high) < 0) {
            BigInteger mid = low.add(high).add(BigInteger.ONE).divide(BigInteger.TWO);
            BigDecimal candidate = new BigDecimal(mid);
            if (monthlyPayment(new LoanTerms(candidate, request.termMonths(), rate)).compareTo(budget) <= 0) low = mid;
            else high = mid.subtract(BigInteger.ONE);
        }
        return new BigDecimal(low).setScale(2);
    }

    private Assessment stopped(RiskInputs inputs, Outcome outcome, boolean complete, List<RiskReason> reasons) {
        return new Assessment(policy.version(), inputs == null ? null : inputs.asOf(), outcome, null, null, List.of(), reasons,
                complete, false, null, null, inputs == null ? null : inputs.existingDebtPaymentRub(), null, null, null, null);
    }

    private void checkSource(List<RiskReason> problems, SourceEvidence source, String expected, String code,
                             LocalDate asOf, int maximumAgeDays) {
        if (source == null || source.source() == null || !source.source().equals(expected)
                || source.version() == null || source.version().isBlank() || source.observedOn() == null) {
            problems.add(reason(code + "_SOURCE_MISSING", Severity.DATA_QUALITY, "Нет полной маркировки синтетического источника " + code + "."));
        } else if (source.observedOn().isAfter(asOf)) {
            problems.add(reason(code + "_SOURCE_FUTURE", Severity.DATA_QUALITY, "Дата источника " + code + " находится в будущем относительно snapshot."));
        } else if (ChronoUnit.DAYS.between(source.observedOn(), asOf) > maximumAgeDays) {
            problems.add(reason(code + "_SOURCE_STALE", Severity.DATA_QUALITY, "Источник " + code + " старше " + maximumAgeDays + " дней; автоматическое решение недоступно."));
        }
    }

    private static boolean sourceFresh(SourceEvidence source, String expected, LocalDate asOf, int maximumAgeDays) {
        return source != null && expected.equals(source.source()) && source.version() != null && !source.version().isBlank()
                && source.observedOn() != null && !source.observedOn().isAfter(asOf)
                && ChronoUnit.DAYS.between(source.observedOn(), asOf) <= maximumAgeDays;
    }
    private boolean recentChange(LocalDate changedOn, LocalDate asOf) { return changedOn != null && ChronoUnit.DAYS.between(changedOn, asOf) < policy.stops().managementChangeDays(); }
    private static boolean validPastDate(LocalDate date, LocalDate asOf) { return date != null && !date.isAfter(asOf); }
    private static boolean validMoney(BigDecimal amount) { return amount.stripTrailingZeros().scale() <= 2; }
    private static boolean wholeRubles(BigDecimal amount) { return amount.stripTrailingZeros().scale() <= 0; }
    private static boolean validRate(BigDecimal rate) { return rate != null && rate.signum() >= 0 && rate.compareTo(MAXIMUM_RATE) <= 0 && rate.stripTrailingZeros().scale() <= 4; }
    private static BigDecimal debtRatio(BigDecimal totalPayments, BigDecimal incomeNumerator) { return totalPayments.multiply(SIX).divide(incomeNumerator, MATH); }
    private static boolean withinRatio(BigDecimal totalPayments, BigDecimal incomeNumerator, BigDecimal threshold) { return totalPayments.multiply(SIX).compareTo(incomeNumerator.multiply(threshold)) <= 0; }
    private static List<MonthlyCashflow> orderedMonths(RiskInputs inputs) { return inputs.cashflows().stream().sorted(Comparator.comparing(MonthlyCashflow::month)).toList(); }
    private static BigDecimal sum(List<MonthlyCashflow> months) { return months.stream().map(MonthlyCashflow::netCreditTurnoverRub).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static RiskReason reason(String code, Severity severity, String message) { return new RiskReason(code, severity, message); }
}
