package ru.alfahackaton.persistance.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Сущность пользователя системы и финансовых показателей РКО.
 */
@Table("users")
public record UserEntity(
        @Id Long id,
        String username,
        String role,
        String companyName,
        String inn,
        Long avgMonthlyTurnoverRub,
        Long annualTurnoverRub,
        Long currentBalanceRub,
        Integer monthsWithBank,
        OffsetDateTime createdAt
) {}
