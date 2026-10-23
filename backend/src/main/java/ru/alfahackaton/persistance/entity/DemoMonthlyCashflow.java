package ru.alfahackaton.persistance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemoMonthlyCashflow(long userId, LocalDate month, BigDecimal turnoverRub,
                                  String source, String sourceVersion, LocalDate asOf) {}
