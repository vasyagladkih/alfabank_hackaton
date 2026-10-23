package ru.alfahackaton.persistance.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SnapshotDocument(long applicationId, String schemaVersion, LocalDate asOf,
                               OffsetDateTime capturedAt, String origin, String payloadJson,
                               String payloadSha256) {}
