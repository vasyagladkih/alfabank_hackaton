package ru.alfahackaton.risk;

import java.math.BigDecimal;

public record LoanTerms(BigDecimal amountRub, int termMonths, BigDecimal annualRatePercent) {}
