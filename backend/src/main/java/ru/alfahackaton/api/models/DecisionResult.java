package ru.alfahackaton.api.models;

/**
 * Итоговое решение скорингового конвейера по заявке.
 */
public record DecisionResult(
        String status,
        String rejectionReason,
        String reviewReasonType,
        Integer scoringScore,
        Double calculatedPdnPercent,
        Long maxLimitRub,
        Long approvedAmount,
        Double approvedRate
) {}
