package ru.alfahackaton.persistance.entity;

import java.time.LocalDate;

public record DemoRegistryFacts(long userId, Boolean taxBlocked, Boolean bankrupt, Boolean liquidating,
                                String source, String sourceVersion, LocalDate asOf) {}
