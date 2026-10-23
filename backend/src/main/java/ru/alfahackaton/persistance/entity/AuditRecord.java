package ru.alfahackaton.persistance.entity;

import java.time.OffsetDateTime;

public record AuditRecord(long id, long applicationId, String eventKey, String eventType,
                          String actorType, Long actorUserId, String fromStatus, String toStatus,
                          long applicationVersion, String rationale, String payloadJson,
                          OffsetDateTime createdAt) {}
