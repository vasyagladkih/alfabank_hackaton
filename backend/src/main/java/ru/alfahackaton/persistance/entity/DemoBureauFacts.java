package ru.alfahackaton.persistance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemoBureauFacts(long userId, Integer score, BigDecimal monthlyDebtRub,
                              Integer maxOverdueDays, Integer recentClosedOverdueDays,
                              String source, String sourceVersion, LocalDate asOf) {}
