package ru.alfahackaton.risk;

import java.math.BigDecimal;

/** value is months, RUB, bureau points, or a CV fraction according to code. */
public record ScoreFactor(String code, String label, BigDecimal value, int points, int maximum,
                          String explanation) {}
