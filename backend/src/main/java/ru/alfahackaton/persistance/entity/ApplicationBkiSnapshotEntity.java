package ru.alfahackaton.persistance.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Снимок данных кредитного отчета БКИ по заявке.
 */
@Table("application_bki_snapshots")
public record ApplicationBkiSnapshotEntity(
        @Id Long id,
        Long applicationId,
        String inn,
        Integer bkiScore,
        Long monthlyPaymentRub,
        Integer currentOverdueDays,
        Integer cntOverdueOver90d,
        String riskBucket,
        OffsetDateTime createdAt
) {}
