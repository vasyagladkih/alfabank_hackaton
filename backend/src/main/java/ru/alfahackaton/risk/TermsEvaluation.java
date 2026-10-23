package ru.alfahackaton.risk;

import java.math.BigDecimal;
import java.util.List;

public record TermsEvaluation(boolean allowed, List<RiskReason> reasons, BigDecimal monthlyPaymentRub,
                              BigDecimal debtRatio, BigDecimal maxAffordableAmountRub) {
    public TermsEvaluation { reasons = List.copyOf(reasons); }
}
