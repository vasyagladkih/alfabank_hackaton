package ru.alfahackaton.stub.fns.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table("mock_fns_registry")
public record MockFnsEntity(
        @Id String inn,
        String companyName,
        String stateStatus,
        Boolean taxBlocksExist,
        LocalDate registrationDate
) {}
