package ru.alfahackaton.demo;

public record DemoScenario(String code, String title, String owner, long amountRub, int termMonths,
                           String purpose, String expectedOutcome, String expectedPath, String snapshotJson) {}
