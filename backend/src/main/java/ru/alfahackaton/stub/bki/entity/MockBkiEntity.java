package ru.alfahackaton.stub.bki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("mock_bki_registry")
public record MockBkiEntity(
        @Id String inn,
        Integer bkiScore,
        Long totalMonthlyPaymentRub,
        Integer currentOverdueDays,
        Integer cntOverdueOver90d
) {}
