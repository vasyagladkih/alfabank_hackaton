package ru.alfahackaton.api.models;


public record FnsDto(
        String inn,
        String companyName,
        String stateStatus,
        Boolean hasTaxBlocks,
        Integer businessAgeMonths
) {}
