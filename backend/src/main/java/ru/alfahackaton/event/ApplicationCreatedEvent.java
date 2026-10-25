package ru.alfahackaton.event;

public record ApplicationCreatedEvent(
        Long applicationId,
        String inn
) {}
