package ru.alfahackaton.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable synthetic inputs. Missing facts remain null; evaluation uses only the captured asOf. */
public record RiskInputs(LocalDate asOf, LocalDate registeredOn, LocalDate accountOpenedOn,
                         LocalDate directorChangedOn, LocalDate soleFounderChangedOn,
                         Boolean managementHistoryComplete, List<MonthlyCashflow> cashflows,
                         BigDecimal existingDebtPaymentRub, Integer bureauScore,
                         Integer maxOverdueDays24m, Integer recentClosedOverdueDays12m,
                         Boolean taxBlocked, Boolean bankrupt, Boolean liquidating,
                         SourceEvidence internalSource, SourceEvidence bureauSource, SourceEvidence fnsSource) {
    public RiskInputs {
        // Keep null lists/items so malformed stored inputs produce a review outcome, not a retry loop.
        if (cashflows != null) cashflows = Collections.unmodifiableList(new ArrayList<>(cashflows));
    }
}
