package ru.alfahackaton.risk;

import java.time.LocalDate;

/** Provider identity, fixture version and observation date copied into the immutable snapshot. */
public record SourceEvidence(String source, String version, LocalDate observedOn) {}
