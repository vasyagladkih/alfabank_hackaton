package ru.alfahackaton.service;

import org.springframework.stereotype.Service;
import ru.alfahackaton.api.models.BorrowerDossier;
import ru.alfahackaton.api.models.DecisionResult;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.ReviewReasonCode;
import ru.alfahackaton.persistance.entity.ApplicationBkiSnapshotEntity;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.ApplicationFnsSnapshotEntity;
import ru.alfahackaton.persistance.entity.UserEntity;

/**
 * Скоринговый движок принятия решений по кредитным заявкам ММБ.
 * Реализует проверку стоп-факторов, расчет ПДН, кредитного лимита и скор-карту заемщика.
 */
@Service
public class DecisionEngine {

    private static final double BASE_INTEREST_RATE_YEARLY = 0.21; // 21% годовых
    private static final long MIN_CREDIT_AMOUNT = 100_000L;

    /**
     * Оценка досье заемщика и формирование кредитного решения.
     *
     * @param dossier собранное кредитное досье клиента со снимками из БД
     * @return DecisionResult с резолюцией и расчетными параметрами
     */
    public DecisionResult evaluate(BorrowerDossier dossier) {
        ApplicationEntity app = dossier.application();
        UserEntity user = dossier.user();
        ApplicationFnsSnapshotEntity fns = dossier.fns();
        ApplicationBkiSnapshotEntity bki = dossier.bki();

        // 1. Проверка стоп-факторов ФНС (Fail-Fast)
        if (fns != null) {
            if ("BANKRUPT".equalsIgnoreCase(fns.stateStatus()) || "LIQUIDATING".equalsIgnoreCase(fns.stateStatus())) {
                return new DecisionResult(
                        ApplicationStatus.REJECTED.getValue(),
                        "Отказ: организация находится в процедуре банкротства или ликвидации",
                        null, null, null, null, null, null
                );
            }
            if (Boolean.TRUE.equals(fns.taxBlocksExist())) {
                return new DecisionResult(
                        ApplicationStatus.REJECTED.getValue(),
                        "Отказ: действующие блокировки банковских счетов по ст. 76 НК РФ",
                        null, null, null, null, null, null
                );
            }
        }

        // 2. Проверка стоп-факторов БКИ (Fail-Fast)
        if (bki != null) {
            if (bki.cntOverdueOver90d() != null && bki.cntOverdueOver90d() > 0) {
                return new DecisionResult(
                        ApplicationStatus.REJECTED.getValue(),
                        "Отказ: наличие критических просрочек платежей свыше 90 дней (дефолт)",
                        null, null, null, null, null, null
                );
            }
            if (bki.currentOverdueDays() != null && bki.currentOverdueDays() > 30) {
                return new DecisionResult(
                        ApplicationStatus.REJECTED.getValue(),
                        "Отказ: наличие непогашенной текущей просроченной задолженности свыше 30 дней",
                        null, null, null, null, null, null
                );
            }
        }

        // 3. Проверка доступности внешних реестров (технический сбой)
        if (fns == null || bki == null) {
            String missing = (fns == null ? "ФНС " : "") + (bki == null ? "БКИ " : "");
            return new DecisionResult(
                    ApplicationStatus.MANUAL_REVIEW.getValue(),
                    "Требуется ручной андеррайтинг: внешний реестр (" + missing.trim() + ") временно недоступен",
                    ReviewReasonCode.TECH_FAILURE.getValue(),
                    null, null, null, null, null
            );
        }

        // 4. Финансовые расчеты (ПДН и аннуитет)
        long requestedAmount = app.amount();
        int termMonths = Math.max(1, app.termMonths());

        long avgTurnover = user.avgMonthlyTurnoverRub() != null ? user.avgMonthlyTurnoverRub() : 0L;
        long annualTurnover = user.annualTurnoverRub() != null ? user.annualTurnoverRub() : avgTurnover * 12L;
        long monthlyNetIncome = Math.round(avgTurnover * 0.25); // 25% чистый операционный поток от оборота РКО

        double monthlyRate = BASE_INTEREST_RATE_YEARLY / 12.0;
        double annuityFactor = (monthlyRate * Math.pow(1.0 + monthlyRate, termMonths))
                / (Math.pow(1.0 + monthlyRate, termMonths) - 1.0);

        long newMonthlyPayment = Math.round(requestedAmount * annuityFactor);
        long existingMonthlyPayment = bki.monthlyPaymentRub() != null ? bki.monthlyPaymentRub() : 0L;

        double pdnPercent;
        if (monthlyNetIncome <= 0) {
            pdnPercent = 100.0;
        } else {
            pdnPercent = ((double) (existingMonthlyPayment + newMonthlyPayment) / monthlyNetIncome) * 100.0;
            pdnPercent = Math.round(pdnPercent * 10.0) / 10.0;
        }

        // 5. Расчет максимального кредитного лимита
        long limitByTurnover = Math.round(annualTurnover * 0.30); // максимум 30% годового оборота
        long maxAllowedMonthlyPayment = Math.max(0L, Math.round(monthlyNetIncome * 0.50) - existingMonthlyPayment);
        long limitByPdn = maxAllowedMonthlyPayment > 0 ? Math.round(maxAllowedMonthlyPayment / annuityFactor) : 0L;
        long maxLimit = Math.min(limitByTurnover, limitByPdn);

        // 6. Скор-карта заемщика (0 - 100 баллов)
        int rkoScore = 5;
        if (avgTurnover >= 1_000_000L) {
            rkoScore = 20;
        } else if (avgTurnover >= 300_000L) {
            rkoScore = 12;
        }
        int monthsWithBank = user.monthsWithBank() != null ? user.monthsWithBank() : 0;
        if (monthsWithBank >= 12) {
            rkoScore += 15;
        } else if (monthsWithBank >= 6) {
            rkoScore += 10;
        } else {
            rkoScore += 3;
        }

        int totalScore = getTotalScore(bki, fns, rkoScore);

        // 7. Проверка ПДН и скорингового балла
        if (pdnPercent > 80.0) {
            return new DecisionResult(
                    ApplicationStatus.REJECTED.getValue(),
                    String.format("Превышен показатель предельной долговой нагрузки (ПДН = %.1f%% при допустимых 80%%)", pdnPercent),
                    null, totalScore, pdnPercent, maxLimit, null, null
            );
        }

        if (totalScore < 50) {
            return new DecisionResult(
                    ApplicationStatus.REJECTED.getValue(),
                    String.format("Скоринговый балл заемщика (%d/100) ниже проходного порога", totalScore),
                    null, totalScore, pdnPercent, maxLimit, null, null
            );
        }

        if (totalScore < 65) {
            return new DecisionResult(
                    ApplicationStatus.MANUAL_REVIEW.getValue(),
                    String.format("Пограничный скоринговый балл (%d/100). Требуется решение кредитного эксперта", totalScore),
                    ReviewReasonCode.BORDERLINE_SCORE.getValue(),
                    totalScore, pdnPercent, maxLimit, null, null
            );
        }

        // 8. Одобрение кредита и расчет индивидуальной ставки
        long approvedAmount = Math.min(requestedAmount, maxLimit);
        if (approvedAmount < MIN_CREDIT_AMOUNT) {
            return new DecisionResult(
                    ApplicationStatus.REJECTED.getValue(),
                    "Отказ: максимально допустимый кредитный лимит ниже минимальной суммы (100 000 руб.)",
                    null, totalScore, pdnPercent, maxLimit, null, null
            );
        }

        double individualRate = Math.round((24.0 - (totalScore - 65) * 0.15) * 10.0) / 10.0;
        individualRate = Math.clamp(individualRate, 16.5, 23.5);

        return new DecisionResult(
                ApplicationStatus.APPROVED.getValue(),
                null,
                null,
                totalScore,
                pdnPercent,
                maxLimit,
                approvedAmount,
                individualRate
        );
    }

    private int getTotalScore(ApplicationBkiSnapshotEntity bki, ApplicationFnsSnapshotEntity fns, int rkoScore) {
        int bkiScore = 0;
        int rawBkiScore = bki.bkiScore() != null ? bki.bkiScore() : 300;
        if (rawBkiScore >= 700) {
            bkiScore = 35;
        } else if (rawBkiScore >= 600) {
            bkiScore = 25;
        } else if (rawBkiScore >= 500) {
            bkiScore = 15;
        }

        int fnsScore = 0;
        int ageMonths = fns.businessAgeMonths() != null ? fns.businessAgeMonths() : 0;
        if (ageMonths >= 24) {
            fnsScore = 30;
        } else if (ageMonths >= 12) {
            fnsScore = 20;
        } else if (ageMonths >= 6) {
            fnsScore = 10;
        }

        return rkoScore + bkiScore + fnsScore;
    }
}
