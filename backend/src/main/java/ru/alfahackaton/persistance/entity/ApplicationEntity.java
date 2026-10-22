package ru.alfahackaton.persistance.entity;

import java.time.OffsetDateTime;

public record ApplicationEntity(
        Long id,
        Long userId,
        Long amount,
        Integer termMonths,
        String purpose,
        String status,
        String rejectionReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String companyName,
        String inn
) {
    public ApplicationEntity(
            Long id,
            Long userId,
            Long amount,
            Integer termMonths,
            String purpose,
            String status,
            String rejectionReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, userId, amount, termMonths, purpose, status, rejectionReason, createdAt, updatedAt, null, null);
    }
}
