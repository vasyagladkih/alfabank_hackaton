package ru.alfahackaton.persistance.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Сущность пользователя системы.
 */
@Table("users")
public record UserEntity(
        @Id Long id,
        String username,
        String role,
        String companyName,
        String inn,
        OffsetDateTime createdAt
) {}