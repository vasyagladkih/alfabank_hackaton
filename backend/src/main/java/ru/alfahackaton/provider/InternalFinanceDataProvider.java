package ru.alfahackaton.provider;

import ru.alfahackaton.risk.MonthlyCashflow;
import ru.alfahackaton.risk.SourceEvidence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Replaceable source adapter. The shipped implementation reads explicitly synthetic local rows. */
public interface InternalFinanceDataProvider {
    Data fetch(long userId, LocalDate asOf);

    record Data(LocalDate registeredOn, LocalDate accountOpenedOn, LocalDate directorChangedOn,
                LocalDate soleFounderChangedOn, Boolean managementHistoryComplete,
                BigDecimal monthlyDebtRub, List<MonthlyCashflow> cashflows, SourceEvidence source) {
        public Data { cashflows = List.copyOf(cashflows); }
    }
}
