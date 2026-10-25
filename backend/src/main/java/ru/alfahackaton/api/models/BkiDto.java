package ru.alfahackaton.api.models;

public record BkiDto(
        String inn,
        Integer bkiScore,
        Long monthlyPaymentRub,
        Integer currentOverdueDays,
        Integer cntOverdue90
) {}
