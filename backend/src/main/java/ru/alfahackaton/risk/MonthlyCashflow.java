package ru.alfahackaton.risk;

import java.math.BigDecimal;
import java.time.YearMonth;

/** One complete calendar month; internal transfers have already been excluded by the mock provider. */
public record MonthlyCashflow(YearMonth month, BigDecimal netCreditTurnoverRub) {}
