package ru.alfahackaton.provider;

import ru.alfahackaton.risk.SourceEvidence;
import java.time.LocalDate;

public interface FnsDataProvider {
    Data fetch(long userId, LocalDate asOf);
    record Data(Boolean taxBlocked, Boolean bankrupt, Boolean liquidating, SourceEvidence source) {}
}
