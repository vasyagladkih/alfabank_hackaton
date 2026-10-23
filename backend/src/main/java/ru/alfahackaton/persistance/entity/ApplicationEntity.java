package ru.alfahackaton.persistance.entity;

import java.time.OffsetDateTime;
import java.math.BigDecimal;

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
        String inn,
        long version,
        String decisionSummary,
        BigDecimal offeredAmount,
        Integer offeredTermMonths,
        BigDecimal offeredAnnualRate,
        BigDecimal offeredMonthlyPayment,
        String ruleVersion,
        OffsetDateTime assessedAt
) {
    public ApplicationEntity(Long id, Long userId, Long amount, Integer termMonths, String purpose,
                             String status, String rejectionReason, OffsetDateTime createdAt,
                             OffsetDateTime updatedAt, String companyName, String inn) {
        this(id, userId, amount, termMonths, purpose, status, rejectionReason, createdAt, updatedAt,
                companyName, inn, 0, null, null, null, null, null, null, null);
    }
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
