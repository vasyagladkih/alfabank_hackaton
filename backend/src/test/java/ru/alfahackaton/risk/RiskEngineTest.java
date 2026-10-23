package ru.alfahackaton.risk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiskEngineTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 23);
    private static final LoanRequest REQUEST = new LoanRequest(money("1000000"), 24);
    private final RiskEngine engine = new RiskEngine(DemoRiskPolicy.defaults());

    @Test void completeGreenProfileGetsAnExplainedDemoApproval() {
        Assessment result = engine.assess(new Inputs().build(), REQUEST);
        assertEquals(Outcome.APPROVED, result.outcome());
        assertEquals(850, result.score());
        assertEquals(ScoreBand.GREEN, result.scoreBand());
        assertTrue(result.evidenceComplete());
        assertTrue(result.eligibleForManualApproval());
        assertEquals(4, result.scoreFactors().size());
        assertEquals(850, result.scoreFactors().stream().mapToInt(ScoreFactor::points).sum());
        assertEquals("DEMO_RULES_V1", result.ruleVersion());
        assertEquals(AS_OF, result.asOf());
        assertNull(result.offer());
        assertMoney("1050000", result.monthlyIncomeRub());
    }

    @ParameterizedTest @CsvSource({"499,RED", "500,GRAY", "649,GRAY", "650,GREEN", "850,GREEN"})
    void scoreBandBoundariesAreExplicit(int score, ScoreBand expected) {
        assertEquals(expected, engine.scoreBand(score));
    }

    @Test void outOfRangeScoresAreRejectedAsProgrammingInput() {
        assertThrows(IllegalArgumentException.class, () -> engine.scoreBand(-1));
        assertThrows(IllegalArgumentException.class, () -> engine.scoreBand(851));
    }

    @ParameterizedTest @CsvSource({"TAX_BLOCK", "BANKRUPTCY", "LIQUIDATION"})
    void knownFreshHardStopsOverrideOtherMissingEvidence(String code) {
        Inputs inputs = new Inputs();
        inputs.bureauScore = null;
        inputs.taxBlocked = code.equals("TAX_BLOCK");
        inputs.bankrupt = code.equals("BANKRUPTCY");
        inputs.liquidating = code.equals("LIQUIDATION");
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.REJECTED, result.outcome());
        assertReason(result, code);
        assertFalse(result.eligibleForManualApproval());
    }

    @Test void sixCalendarMonthsOfBusinessHistoryPassButOneDayLessDoesNot() {
        Inputs inputs = new Inputs();
        inputs.registeredOn = AS_OF.minusMonths(6);
        assertEquals(Outcome.APPROVED, engine.assess(inputs.build(), REQUEST).outcome());
        inputs.registeredOn = inputs.registeredOn.plusDays(1);
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.REJECTED, result.outcome());
        assertReason(result, "BUSINESS_TOO_YOUNG");
    }

    @Test void overdueNinetyIsManualAndNinetyOneIsHardStop() {
        Inputs inputs = new Inputs();
        inputs.maxOverdueDays24m = 90;
        Assessment ninety = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.MANUAL_REVIEW, ninety.outcome());
        assertReason(ninety, "MODERATE_OVERDUE_HISTORY");
        inputs.maxOverdueDays24m = 91;
        Assessment ninetyOne = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.REJECTED, ninetyOne.outcome());
        assertReason(ninetyOne, "SEVERE_OVERDUE");
    }

    @Test void managementChangesUseStrictSixtyDayBoundary() {
        Inputs inputs = new Inputs();
        inputs.directorChangedOn = AS_OF.minusDays(60);
        assertEquals(Outcome.APPROVED, engine.assess(inputs.build(), REQUEST).outcome());
        inputs.directorChangedOn = AS_OF.minusDays(59);
        assertReason(engine.assess(inputs.build(), REQUEST), "RECENT_DIRECTOR_CHANGE");
        inputs.directorChangedOn = null;
        inputs.soleFounderChangedOn = AS_OF.minusDays(59);
        assertReason(engine.assess(inputs.build(), REQUEST), "RECENT_FOUNDER_CHANGE");
        inputs.soleFounderChangedOn = AS_OF.minusDays(60);
        assertEquals(Outcome.APPROVED, engine.assess(inputs.build(), REQUEST).outcome());
    }

    @Test void unknownManagementHistoryIsNotEquivalentToNoChanges() {
        Inputs inputs = new Inputs();
        inputs.managementHistoryComplete = null;
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.MANUAL_REVIEW, result.outcome());
        assertFalse(result.evidenceComplete());
        assertReason(result, "MANAGEMENT_HISTORY_MISSING");
    }

    @Test void turnoverDropUsesAllTwelveMonthsAndStrictThirtyFivePercentBoundary() {
        Inputs inputs = new Inputs();
        inputs.cashflows = months("1135000");
        inputs.cashflows.set(11, month(0, "715000")); // annual mean 1.1m; exact 35% fall
        assertEquals(Outcome.APPROVED, engine.assess(inputs.build(), REQUEST).outcome());
        inputs.cashflows.set(11, month(0, "714999"));
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.MANUAL_REVIEW, result.outcome());
        assertReason(result, "TURNOVER_DROP");
    }

    @ParameterizedTest @CsvSource({"1", "30"})
    void closedShortOverdueRequiresReview(int days) {
        Inputs inputs = new Inputs();
        inputs.recentClosedOverdueDays12m = days;
        inputs.maxOverdueDays24m = days;
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.MANUAL_REVIEW, result.outcome());
        assertReason(result, "CLOSED_SHORT_OVERDUE");
    }

    @ParameterizedTest @CsvSource({"5,0", "6,80", "11,80", "12,150", "24,150", "25,200"})
    void accountTenureFactorsHaveNoOverlappingBoundaries(int months, int points) {
        Inputs inputs = new Inputs();
        inputs.accountOpenedOn = AS_OF.minusMonths(months);
        assertEquals(points, factor(engine.assess(inputs.build(), REQUEST), "ACCOUNT_TENURE").points());
    }

    @ParameterizedTest @CsvSource({"499999.99,50", "500000,120", "999999.99,120", "1000000,190", "2000000,190", "2000000.01,250"})
    void turnoverFactorBoundariesAreUnambiguous(String turnover, int points) {
        Inputs inputs = new Inputs();
        inputs.cashflows = months(turnover);
        assertEquals(points, factor(engine.assess(inputs.build(), REQUEST), "TURNOVER").points());
    }

    @ParameterizedTest @CsvSource({"0,40", "549,40", "550,150", "699,150", "700,250", "1000,250"})
    void bureauScoreFactorBoundariesAreUnambiguous(int bureauScore, int points) {
        Inputs inputs = new Inputs();
        inputs.bureauScore = bureauScore;
        assertEquals(points, factor(engine.assess(inputs.build(), REQUEST), "BUREAU_SCORE").points());
    }

    @ParameterizedTest @CsvSource({"850000,1150000,90", "700000,1300000,90", "699999,1300001,30", "850001,1149999,150"})
    void cvUsesPopulationVarianceAcrossLatestSixMonths(String low, String high, int points) {
        Inputs inputs = new Inputs();
        inputs.cashflows = months("1000000");
        for (int j = 6; j < 12; j++) inputs.cashflows.set(j, month(11 - j, j % 2 == 0 ? low : high));
        assertEquals(points, factor(engine.assess(inputs.build(), REQUEST), "TURNOVER_STABILITY").points());
    }

    @Test void lowScoreIsNotRescuedBySoftStopOrAffordability() {
        Inputs inputs = new Inputs();
        inputs.accountOpenedOn = AS_OF.minusMonths(6);
        inputs.cashflows = months("400000");
        inputs.bureauScore = 400;
        inputs.directorChangedOn = AS_OF.minusDays(2);
        Assessment result = engine.assess(inputs.build(), new LoanRequest(money("100000"), 60));
        assertEquals(Outcome.REJECTED, result.outcome());
        assertReason(result, "LOW_SCORE");
        assertFalse(result.eligibleForManualApproval());
    }

    @Test void grayScoreRequiresReviewAndExposesValidatedCapacity() {
        Inputs inputs = new Inputs();
        inputs.accountOpenedOn = AS_OF.minusMonths(14);
        inputs.cashflows = months("1500000");
        inputs.bureauScore = 600;
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(640, result.score());
        assertEquals(Outcome.MANUAL_REVIEW, result.outcome());
        assertTrue(result.eligibleForManualApproval());
        assertMoney("18.50", result.annualRatePercent());
        assertMoney("1000000", result.maxAffordableAmountRub());
    }

    @ParameterizedTest @CsvSource({"134999.99,APPROVED", "135000,MANUAL_REVIEW", "152500,MANUAL_REVIEW", "152500.01,OFFER_PROPOSED"})
    void debtRatioBoundariesUseActualDecimalPayments(String debt, Outcome expected) {
        RiskEngine zeroRate = new RiskEngine(DemoRiskPolicy.defaults().withRates("ZERO_RATE_TEST", BigDecimal.ZERO, BigDecimal.ZERO));
        Inputs inputs = new Inputs();
        inputs.cashflows = months("1000000");
        inputs.existingDebtPaymentRub = money(debt);
        Assessment result = zeroRate.assess(inputs.build(), new LoanRequest(money("270000"), 12));
        assertEquals(expected, result.outcome());
        if (expected == Outcome.MANUAL_REVIEW) assertReason(result, "BORDERLINE_DEBT_RATIO");
    }

    @Test void offerIsNeverAboveRequestAndRoundedTermsRemainAffordable() {
        Inputs inputs = new Inputs();
        inputs.existingDebtPaymentRub = money("50000.01");
        LoanRequest large = new LoanRequest(money("50000000"), 24);
        Assessment result = engine.assess(inputs.build(), large);
        assertEquals(Outcome.OFFER_PROPOSED, result.outcome());
        assertNotNull(result.offer());
        assertTrue(result.offer().amountRub().compareTo(large.amountRub()) <= 0);
        assertTrue(result.offer().amountRub().compareTo(money("100000")) >= 0);
        assertEquals(2, result.offer().amountRub().scale());
        TermsEvaluation checked = engine.evaluateTerms(inputs.build(), large, result.offer());
        assertTrue(checked.allowed(), checked.reasons().toString());
        assertTrue(checked.debtRatio().compareTo(money("0.50")) <= 0);
        LoanTerms oneCentMore = new LoanTerms(result.offer().amountRub().add(money("0.01")), 24, result.annualRatePercent());
        assertFalse(engine.evaluateTerms(inputs.build(), large, oneCentMore).allowed());
    }

    @Test void noAffordableMinimumAmountRejectsWithoutCreatingAnOffer() {
        Inputs inputs = new Inputs();
        inputs.existingDebtPaymentRub = money("600000");
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.REJECTED, result.outcome());
        assertReason(result, "NO_AFFORDABLE_OFFER");
        assertNull(result.offer());
        assertMoney("0", result.maxAffordableAmountRub());
    }

    @Test void positiveCapacityBelowMinimumDoesNotBecomeAnOffer() {
        Inputs inputs = new Inputs();
        inputs.existingDebtPaymentRub = money("524999");
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.REJECTED, result.outcome());
        assertReason(result, "NO_AFFORDABLE_OFFER");
        assertTrue(result.maxAffordableAmountRub().compareTo(money("100000")) < 0);
        assertNull(result.offer());
    }

    @Test void zeroInterestUsesLinearPaymentAndRoundsUpToCents() {
        assertMoney("33333.34", RiskEngine.monthlyPayment(new LoanTerms(money("100000"), 3, BigDecimal.ZERO)));
        assertMoney("25000", RiskEngine.monthlyPayment(new LoanTerms(money("300000"), 12, BigDecimal.ZERO)));
    }

    @Test void singleMonthInterestUsesAnnualPercentDividedByTwelve() {
        assertMoney("101375", RiskEngine.monthlyPayment(new LoanTerms(money("100000"), 1, money("16.5"))));
    }

    @Test void offersAndMutationsPreserveExistingWholeRubleApiContract() {
        Inputs inputs = new Inputs();
        inputs.existingDebtPaymentRub = money("50000");
        LoanRequest large = new LoanRequest(money("10000000"), 12);
        Assessment result = engine.assess(inputs.build(), large);
        assertEquals(Outcome.OFFER_PROPOSED, result.outcome());
        assertEquals(0, result.maxAffordableAmountRub().remainder(BigDecimal.ONE).signum());
        assertEquals(0, result.offer().amountRub().remainder(BigDecimal.ONE).signum());
        assertThrows(IllegalArgumentException.class, () -> engine.assess(inputs.build(), new LoanRequest(money("100000.01"), 12)));
        assertFalse(engine.evaluateTerms(inputs.build(), large,
                new LoanTerms(money("100000.01"), 12, result.annualRatePercent())).allowed());
    }

    @Test void missingInputsStayUnknownInsteadOfBecomingGreenZeroes() {
        Inputs inputs = new Inputs();
        inputs.bureauScore = null;
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.MANUAL_REVIEW, result.outcome());
        assertNull(result.score());
        assertFalse(result.evidenceComplete());
        assertFalse(result.eligibleForManualApproval());
        assertNull(result.maxAffordableAmountRub());
        assertFalse(engine.evaluateTerms(inputs.build(), REQUEST, new LoanTerms(REQUEST.amountRub(), 24, money("16.5"))).allowed());
    }

    @Test void absentLegalBooleanIsNotAssumedFalse() {
        Inputs inputs = new Inputs();
        inputs.taxBlocked = null;
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertFalse(result.evidenceComplete());
        assertReason(result, "LEGAL_FACTS_MISSING");
    }

    @Test void sourceFreshnessHasInclusiveThirtyAndFortyFiveDayBoundaries() {
        Inputs inputs = new Inputs();
        inputs.bureauSource = source("BKI", 30);
        inputs.fnsSource = source("FNS", 30);
        inputs.internalSource = source("INTERNAL", 45);
        assertEquals(Outcome.APPROVED, engine.assess(inputs.build(), REQUEST).outcome());
        inputs.bureauSource = source("BKI", 31);
        assertReason(engine.assess(inputs.build(), REQUEST), "BUREAU_SOURCE_STALE");
        inputs.bureauSource = source("BKI", 0);
        inputs.internalSource = source("INTERNAL", 46);
        assertReason(engine.assess(inputs.build(), REQUEST), "INTERNAL_SOURCE_STALE");
    }

    @Test void staleHardStopEvidenceRequiresReviewInsteadOfPretendingItIsCurrent() {
        Inputs inputs = new Inputs();
        inputs.taxBlocked = true;
        inputs.fnsSource = source("FNS", 31);
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.MANUAL_REVIEW, result.outcome());
        assertReason(result, "FNS_SOURCE_STALE");
    }

    @Test void futureDatedOrInvalidDataCannotApprove() {
        Inputs inputs = new Inputs();
        inputs.internalSource = source("INTERNAL", -1);
        assertFalse(engine.assess(inputs.build(), REQUEST).evidenceComplete());
        inputs = new Inputs();
        inputs.existingDebtPaymentRub = money("-0.01");
        assertReason(engine.assess(inputs.build(), REQUEST), "DEBT_INVALID");
        inputs = new Inputs();
        inputs.bureauScore = 1001;
        assertReason(engine.assess(inputs.build(), REQUEST), "BUREAU_SCORE_INVALID");
        inputs = new Inputs();
        inputs.registeredOn = AS_OF.plusDays(1);
        assertEquals(Outcome.MANUAL_REVIEW, engine.assess(inputs.build(), REQUEST).outcome());
    }

    @Test void cashflowWindowMustBeCompleteConsecutiveAndNonnegative() {
        Inputs inputs = new Inputs();
        inputs.cashflows.remove(0);
        assertReason(engine.assess(inputs.build(), REQUEST), "CASHFLOW_WINDOW_INCOMPLETE");
        inputs = new Inputs();
        inputs.cashflows.set(0, inputs.cashflows.get(1));
        assertReason(engine.assess(inputs.build(), REQUEST), "CASHFLOW_WINDOW_INVALID");
        inputs = new Inputs();
        inputs.cashflows.set(0, month(11, "-1"));
        assertReason(engine.assess(inputs.build(), REQUEST), "CASHFLOW_VALUE_INVALID");
    }

    @Test void zeroTurnoverDoesNotDivideByZeroOrInventAStableIncome() {
        Inputs inputs = new Inputs();
        inputs.cashflows = months("0");
        Assessment result = engine.assess(inputs.build(), REQUEST);
        assertEquals(Outcome.MANUAL_REVIEW, result.outcome());
        assertReason(result, "ZERO_INCOME");
        assertNull(result.requestedDebtRatio());
        assertNull(result.offer());
        assertFalse(result.eligibleForManualApproval());
    }

    @Test void snapshotInputsAndResultsCannotBeMutatedByCallers() {
        Inputs inputs = new Inputs();
        RiskInputs snapshot = inputs.build();
        inputs.cashflows.clear();
        assertEquals(12, snapshot.cashflows().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.cashflows().clear());
        Assessment result = engine.assess(snapshot, REQUEST);
        assertThrows(UnsupportedOperationException.class, () -> result.reasons().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.scoreFactors().clear());
    }

    @Test void evaluationIsRepeatableAndIndependentOfInputRowOrder() {
        Inputs inputs = new Inputs();
        Assessment first = engine.assess(inputs.build(), REQUEST);
        Collections.reverse(inputs.cashflows);
        assertEquals(first, engine.assess(inputs.build(), REQUEST));
        assertEquals(first, engine.assess(inputs.build(), REQUEST));
    }

    @Test void employeeMayResolveGrayButCannotAlterTermRateOrBypassAmountBounds() {
        Inputs inputs = new Inputs();
        inputs.directorChangedOn = AS_OF.minusDays(10);
        assertEquals(Outcome.MANUAL_REVIEW, engine.assess(inputs.build(), REQUEST).outcome());
        LoanTerms valid = new LoanTerms(REQUEST.amountRub(), 24, money("16.5"));
        assertTrue(engine.evaluateTerms(inputs.build(), REQUEST, valid).allowed());
        assertFalse(engine.evaluateTerms(inputs.build(), REQUEST, new LoanTerms(money("1000000.01"), 24, money("16.5"))).allowed());
        assertFalse(engine.evaluateTerms(inputs.build(), REQUEST, new LoanTerms(money("99999.99"), 24, money("16.5"))).allowed());
        assertFalse(engine.evaluateTerms(inputs.build(), REQUEST, new LoanTerms(money("1000000"), 25, money("16.5"))).allowed());
        assertFalse(engine.evaluateTerms(inputs.build(), REQUEST, new LoanTerms(money("1000000"), 24, money("0"))).allowed());
        inputs.taxBlocked = true;
        assertFalse(engine.evaluateTerms(inputs.build(), REQUEST, valid).allowed());
    }

    @Test void invalidRequestBoundsFailBeforeRiskEvaluation() {
        for (LoanRequest request : List.of(new LoanRequest(money("99999.99"), 1), new LoanRequest(money("50000000.01"), 1),
                new LoanRequest(money("100000"), 0), new LoanRequest(money("100000"), 61), new LoanRequest(money("-1"), 12))) {
            assertThrows(IllegalArgumentException.class, () -> engine.assess(new Inputs().build(), request));
        }
        assertNotNull(engine.assess(new Inputs().build(), new LoanRequest(money("100000"), 1)));
        assertNotNull(engine.assess(new Inputs().build(), new LoanRequest(money("50000000"), 60)));
    }

    @Test void policyRejectsInvalidRatesAndRequiresNewVersionForChangedRates() {
        assertThrows(IllegalArgumentException.class, () -> DemoRiskPolicy.defaults().withRates("BAD", money("-1"), money("18.5")));
        assertThrows(IllegalArgumentException.class, () -> DemoRiskPolicy.defaults().withRates("", money("16.5"), money("18.5")));
        assertThrows(IllegalArgumentException.class, () -> DemoRiskPolicy.defaults().withRates("TOO_PRECISE", money("0.00001"), money("18.5")));
        assertThrows(IllegalArgumentException.class, () -> DemoRiskPolicy.defaults().withRates("DEMO_RULES_V1", money("0"), money("0")));
    }

    @Test void policyRejectsMisorderedScoreAndDebtThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new DemoRiskPolicy.Scoring(500, 650, 24, 12, 6,
                money("2000000"), money("1000000"), money("500000"), 700, 550, money("0.15"), money("0.30")));
        assertThrows(IllegalArgumentException.class, () -> new DemoRiskPolicy.Affordability(money("0.35"), money("0.50"),
                money("0.45"), money("16.5"), money("18.5")));
    }

    @Test void absentSnapshotDateReturnsManualWithoutReadingTheWallClock() {
        RiskInputs inputs = new Inputs().build();
        RiskInputs noDate = new RiskInputs(null, inputs.registeredOn(), inputs.accountOpenedOn(), null, null, true,
                inputs.cashflows(), inputs.existingDebtPaymentRub(), inputs.bureauScore(), 0, 0, false, false, false,
                inputs.internalSource(), inputs.bureauSource(), inputs.fnsSource());
        assertReason(engine.assess(noDate, REQUEST), "SNAPSHOT_DATE_MISSING");
        assertEquals(Outcome.MANUAL_REVIEW, engine.assess(null, REQUEST).outcome());
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void assertMoney(String value, BigDecimal actual) { assertNotNull(actual); assertEquals(0, money(value).compareTo(actual)); }
    private static void assertReason(Assessment result, String code) {
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.code().equals(code)), "Missing " + code + ": " + result.reasons());
    }
    private static ScoreFactor factor(Assessment result, String code) {
        return result.scoreFactors().stream().filter(f -> f.code().equals(code)).findFirst().orElseThrow();
    }
    private static SourceEvidence source(String kind, int ageDays) {
        return new SourceEvidence("SYNTHETIC_" + kind, "FIXTURE_V1", AS_OF.minusDays(ageDays));
    }
    private static MonthlyCashflow month(int monthsAgo, String amount) {
        return new MonthlyCashflow(YearMonth.from(AS_OF).minusMonths(monthsAgo + 1), money(amount));
    }
    private static List<MonthlyCashflow> months(String amount) {
        List<MonthlyCashflow> rows = new ArrayList<>();
        for (int j = 11; j >= 0; j--) rows.add(month(j, amount));
        return rows;
    }

    private static final class Inputs {
        LocalDate registeredOn = LocalDate.of(2019, 1, 1);
        LocalDate accountOpenedOn = LocalDate.of(2020, 1, 1);
        LocalDate directorChangedOn;
        LocalDate soleFounderChangedOn;
        Boolean managementHistoryComplete = true;
        List<MonthlyCashflow> cashflows = months("3000000");
        BigDecimal existingDebtPaymentRub = money("0");
        Integer bureauScore = 750;
        Integer maxOverdueDays24m = 0;
        Integer recentClosedOverdueDays12m = 0;
        Boolean taxBlocked = false;
        Boolean bankrupt = false;
        Boolean liquidating = false;
        SourceEvidence internalSource = source("INTERNAL", 0);
        SourceEvidence bureauSource = source("BKI", 0);
        SourceEvidence fnsSource = source("FNS", 0);

        RiskInputs build() {
            return new RiskInputs(AS_OF, registeredOn, accountOpenedOn, directorChangedOn, soleFounderChangedOn,
                    managementHistoryComplete, cashflows, existingDebtPaymentRub, bureauScore, maxOverdueDays24m,
                    recentClosedOverdueDays12m, taxBlocked, bankrupt, liquidating, internalSource, bureauSource, fnsSource);
        }
    }
}
