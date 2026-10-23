package ru.alfahackaton.risk;

import java.math.BigDecimal;

public record LoanRequest(BigDecimal amountRub, int termMonths) {}
