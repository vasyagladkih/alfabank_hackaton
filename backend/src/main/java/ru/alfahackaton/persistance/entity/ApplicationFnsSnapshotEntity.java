package ru.alfahackaton.persistance.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Снимок данных внешнего сервиса ФНС по кредитной заявке.
 */
@Table("application_fns_snapshots")
public record ApplicationFnsSnapshotEntity(
        @Id Long id,
        Long applicationId,
        String inn,
        String companyName,
        String stateStatus,
        Boolean taxBlocksExist,
        Integer businessAgeMonths,
        OffsetDateTime createdAt
) {}
