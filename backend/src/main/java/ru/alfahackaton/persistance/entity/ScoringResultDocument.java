package ru.alfahackaton.persistance.entity;

import java.time.OffsetDateTime;

public record ScoringResultDocument(long applicationId, String ruleVersion, String payloadJson,
                                    OffsetDateTime createdAt) {}
