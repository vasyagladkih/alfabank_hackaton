package ru.alfahackaton.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A demonstration recommendation, never a calibrated probability or a real lending decision. */
public record Assessment(String ruleVersion, LocalDate asOf, Outcome outcome, Integer score, ScoreBand scoreBand,
                         List<ScoreFactor> scoreFactors, List<RiskReason> reasons, boolean evidenceComplete,
                         boolean eligibleForManualApproval, BigDecimal annualRatePercent,
                         BigDecimal monthlyIncomeRub, BigDecimal existingDebtPaymentRub,
                         BigDecimal requestedPaymentRub, BigDecimal requestedDebtRatio,
                         BigDecimal maxAffordableAmountRub, LoanTerms offer) {
    public Assessment {
        scoreFactors = List.copyOf(scoreFactors);
        reasons = List.copyOf(reasons);
    }
}
