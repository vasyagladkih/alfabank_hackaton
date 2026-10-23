package ru.alfahackaton.risk;

import java.math.BigDecimal;
import java.util.Objects;

/** Explicit immutable demo assumptions. Construct a new named version when changing settings. */
public record DemoRiskPolicy(String version, Product product, Stops stops, Freshness freshness,
                             Scoring scoring, Affordability affordability) {
    public DemoRiskPolicy {
        if (version == null || version.isBlank() || version.length() > 64) throw new IllegalArgumentException("Rule version is required (1–64 characters)");
        Objects.requireNonNull(product); Objects.requireNonNull(stops); Objects.requireNonNull(freshness);
        Objects.requireNonNull(scoring); Objects.requireNonNull(affordability);
    }

    public static DemoRiskPolicy defaults() {
        return new DemoRiskPolicy("DEMO_RULES_V1",
                new Product(decimal("100000"), decimal("50000000"), 1, 60),
                new Stops(6, 60, 90, 30, decimal("0.35")),
                new Freshness(45, 30, 30),
                new Scoring(650, 500, 24, 12, 6, decimal("2000000"), decimal("1000000"), decimal("500000"),
                        700, 550, decimal("0.15"), decimal("0.30")),
                new Affordability(decimal("0.35"), decimal("0.45"), decimal("0.50"), decimal("16.50"), decimal("18.50")));
    }

    public DemoRiskPolicy withRates(String newVersion, BigDecimal green, BigDecimal manual) {
        if (version.equals(newVersion) && (!affordability.greenAnnualRatePercent().equals(green)
                || !affordability.manualAnnualRatePercent().equals(manual))) {
            throw new IllegalArgumentException("Changed rates require a new rule version");
        }
        return new DemoRiskPolicy(newVersion, product, stops, freshness, scoring,
                new Affordability(affordability.incomeFraction(), affordability.borderlineDebtRatio(),
                        affordability.maximumDebtRatio(), green, manual));
    }

    public record Product(BigDecimal minimumAmountRub, BigDecimal maximumAmountRub, int minimumTermMonths, int maximumTermMonths) {
        public Product {
            positive(minimumAmountRub, "minimum amount"); positive(maximumAmountRub, "maximum amount");
            if (minimumAmountRub.stripTrailingZeros().scale() > 0 || maximumAmountRub.stripTrailingZeros().scale() > 0
                    || minimumAmountRub.compareTo(maximumAmountRub) > 0 || minimumTermMonths < 1 || maximumTermMonths < minimumTermMonths || maximumTermMonths > 60)
                throw new IllegalArgumentException("Invalid demo product bounds");
        }
    }

    public record Stops(int minimumBusinessMonths, int managementChangeDays, int hardOverdueDays,
                        int shortOverdueDays, BigDecimal turnoverDropFraction) {
        public Stops {
            if (minimumBusinessMonths < 0 || managementChangeDays < 1 || shortOverdueDays < 1 || hardOverdueDays < shortOverdueDays)
                throw new IllegalArgumentException("Invalid demo stop thresholds");
            fraction(turnoverDropFraction, "turnover drop");
        }
    }

    public record Freshness(int internalDays, int bureauDays, int fnsDays) {
        public Freshness {
            if (internalDays < 0 || bureauDays < 0 || fnsDays < 0) throw new IllegalArgumentException("Freshness days cannot be negative");
        }
    }

    public record Scoring(int greenMinimum, int manualMinimum, int matureAccountMonths, int establishedAccountMonths,
                          int minimumAccountMonths, BigDecimal highTurnoverRub, BigDecimal mediumTurnoverRub,
                          BigDecimal lowTurnoverRub, int highBureauScore, int mediumBureauScore,
                          BigDecimal lowCv, BigDecimal highCv) {
        public Scoring {
            if (manualMinimum < 0 || greenMinimum <= manualMinimum || greenMinimum > 850 || minimumAccountMonths < 0
                    || establishedAccountMonths <= minimumAccountMonths || matureAccountMonths < establishedAccountMonths
                    || mediumBureauScore < 0 || highBureauScore <= mediumBureauScore || highBureauScore > 1000)
                throw new IllegalArgumentException("Invalid demo score thresholds");
            positive(lowTurnoverRub, "low turnover"); positive(mediumTurnoverRub, "medium turnover"); positive(highTurnoverRub, "high turnover");
            if (highTurnoverRub.compareTo(mediumTurnoverRub) <= 0 || mediumTurnoverRub.compareTo(lowTurnoverRub) <= 0)
                throw new IllegalArgumentException("Turnover thresholds must be ordered");
            fraction(lowCv, "low CV"); fraction(highCv, "high CV");
            if (lowCv.compareTo(highCv) >= 0) throw new IllegalArgumentException("CV thresholds must be ordered");
        }
    }

    public record Affordability(BigDecimal incomeFraction, BigDecimal borderlineDebtRatio, BigDecimal maximumDebtRatio,
                                 BigDecimal greenAnnualRatePercent, BigDecimal manualAnnualRatePercent) {
        public Affordability {
            fraction(incomeFraction, "income fraction"); fraction(borderlineDebtRatio, "borderline ratio"); fraction(maximumDebtRatio, "maximum ratio");
            if (borderlineDebtRatio.compareTo(maximumDebtRatio) >= 0) throw new IllegalArgumentException("Debt ratio thresholds must be ordered");
            rate(greenAnnualRatePercent); rate(manualAnnualRatePercent);
        }
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
    private static void fraction(BigDecimal value, String name) {
        positive(value, name);
        if (value.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException(name + " cannot exceed one");
    }
    private static void rate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(decimal("100")) > 0 || value.stripTrailingZeros().scale() > 4)
            throw new IllegalArgumentException("Demo annual rate must be between 0 and 100 percent, with at most four decimals");
    }
}
