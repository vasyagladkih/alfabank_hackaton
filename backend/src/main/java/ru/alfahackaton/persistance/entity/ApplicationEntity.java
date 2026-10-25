package ru.alfahackaton.persistance.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Сущность кредитной заявки на базе Spring Data JDBC.
 * Содержит параметры кредита, текущий статус и итоговое решение кредитного скоринга.
 */
@Table("applications")
public record ApplicationEntity(
        @Id Long id,
        Long userId,
        Long amount,
        Integer termMonths,
        String purpose,
        String status,
        String rejectionReason,
        String reviewReasonType,
        Integer scoringScore,
        Double calculatedPdnPercent,
        Long maxLimitRub,
        Long approvedAmount,
        Double approvedRate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        @ReadOnlyProperty String companyName,
        @ReadOnlyProperty String inn
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
            OffsetDateTime updatedAt,
            String companyName,
            String inn
    ) {
        this(
                id,
                userId,
                amount,
                termMonths,
                purpose,
                status,
                rejectionReason,
                null,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                updatedAt,
                companyName,
                inn
        );
    }
}
