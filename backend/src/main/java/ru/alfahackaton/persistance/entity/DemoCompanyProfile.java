package ru.alfahackaton.persistance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemoCompanyProfile(long userId, LocalDate registeredOn, LocalDate accountOpenedOn,
                                 LocalDate directorChangedOn, LocalDate soleFounderChangedOn,
                                 Boolean managementHistoryComplete, BigDecimal internalMonthlyDebtRub,
                                 String source, String sourceVersion, LocalDate asOf) {}
