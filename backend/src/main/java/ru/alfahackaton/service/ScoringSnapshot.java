package ru.alfahackaton.service;

import ru.alfahackaton.risk.LoanRequest;
import ru.alfahackaton.risk.RiskInputs;

public record ScoringSnapshot(boolean demoOnly, String ruleVersion, String scenarioCode, String scenarioTitle,
                              String companyName, String inn, String purpose, RiskInputs inputs, LoanRequest request) {}
