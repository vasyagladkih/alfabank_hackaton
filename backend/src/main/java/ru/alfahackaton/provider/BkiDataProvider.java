package ru.alfahackaton.provider;

import ru.alfahackaton.risk.SourceEvidence;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface BkiDataProvider {
    Data fetch(long userId, LocalDate asOf);
    record Data(Integer score, BigDecimal monthlyDebtRub, Integer maxOverdueDays24m,
                Integer recentClosedOverdueDays12m, SourceEvidence source) {}
}
