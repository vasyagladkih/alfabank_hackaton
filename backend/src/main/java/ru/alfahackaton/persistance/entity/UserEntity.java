package ru.alfahackaton.persistance.entity;

import java.time.OffsetDateTime;

public record UserEntity(
        Long id,
        String username,
        String role,
        String companyName,
        String inn,
        OffsetDateTime createdAt
) {}