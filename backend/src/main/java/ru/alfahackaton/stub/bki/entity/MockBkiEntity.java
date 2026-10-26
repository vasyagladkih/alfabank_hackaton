package ru.alfahackaton.stub.bki.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("mock_bki_registry")
public record MockBkiEntity(
        @Id String inn,
        Integer bkiScore,
        Long totalMonthlyPaymentRub,
        Integer currentOverdueDays,
        @Column("cnt_overdue_over_90d") Integer cntOverdueOver90d
) {}

