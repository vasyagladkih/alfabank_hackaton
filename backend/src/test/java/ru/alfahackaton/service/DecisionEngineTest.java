package ru.alfahackaton.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.alfahackaton.api.models.BorrowerDossier;
import ru.alfahackaton.api.models.DecisionResult;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.ReviewReasonCode;
import ru.alfahackaton.persistance.entity.ApplicationBkiSnapshotEntity;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.ApplicationFnsSnapshotEntity;
import ru.alfahackaton.persistance.entity.UserEntity;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionEngineTest {

    private DecisionEngine decisionEngine;

    @BeforeEach
    void setUp() {
        decisionEngine = new DecisionEngine();
    }

    private UserEntity createGoodUser() {
        return new UserEntity(
                101L,
                "client_good",
                "CLIENT",
                "ООО \"Ромашка\"",
                "7701234567",
                1_500_000L,
                18_000_000L,
                350_000L,
                24,
                OffsetDateTime.now()
        );
    }

    private ApplicationEntity createApplication(Long amount, Integer termMonths) {
        return new ApplicationEntity(
                1L,
                101L,
                amount,
                termMonths,
                "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(),
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "ООО \"Ромашка\"",
                "7701234567"
        );
    }

    @Test
    @DisplayName("Качественный заемщик: строгие эталонные значения скоринга (100 баллов, ставка 18.8%)")
    void testGoodBorrowerApprovedStrictValues() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 750, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.APPROVED.getValue(), result.status());
        assertNull(result.rejectionReason());
        assertNull(result.reviewReasonType());
        // РКО (20+15=35) + БКИ (35) + ФНС (30) = строго 100 баллов
        assertEquals(100, result.scoringScore());
        // 24.0 - (100 - 65) * 0.15 = 18.75 -> 18.8%
        assertEquals(18.8, result.approvedRate());
        assertEquals(1_500_000L, result.approvedAmount());
        assertEquals(2_013_666L, result.maxLimitRub());
        assertEquals(37.2, result.calculatedPdnPercent());
    }

    @Test
    @DisplayName("Стоп-фактор ФНС: статус BANKRUPT приводит к мгновенному отказу REJECTED")
    void testFnsBankruptRejection() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "BANKRUPT", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 750, 50_000L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertTrue(result.rejectionReason().contains("банкротства"));
    }

    @Test
    @DisplayName("Стоп-фактор ФНС: статус LIQUIDATING приводит к мгновенному отказу REJECTED")
    void testFnsLiquidatingRejection() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "LIQUIDATING", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 750, 50_000L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertTrue(result.rejectionReason().contains("ликвидации"));
    }

    @Test
    @DisplayName("Стоп-фактор ФНС: блокировка счетов по ст. 76 НК РФ приводит к отказу REJECTED")
    void testFnsTaxBlocksRejection() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "ACTIVE", true, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 750, 50_000L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertTrue(result.rejectionReason().contains("ст. 76 НК РФ"));
    }

    @Test
    @DisplayName("Стоп-фактор БКИ: просрочка свыше 90 дней (дефолт) приводит к отказу REJECTED")
    void testBkiOverdue90Rejection() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 550, 50_000L, 0, 2, "DEFAULT (90+ DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertTrue(result.rejectionReason().contains("90 дней"));
    }

    @Test
    @DisplayName("Стоп-фактор БКИ: активная текущая просрочка свыше 30 дней приводит к отказу REJECTED")
    void testBkiCurrentOverdueOver30Rejection() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 650, 50_000L, 45, 0, "SUBSTANDARD (31-60 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertTrue(result.rejectionReason().contains("свыше 30 дней"));
    }

    @Test
    @DisplayName("Технический сбой БКИ: перевод в MANUAL_REVIEW с кодом TECH_FAILURE")
    void testBkiUnavailableLeadsToManualReview() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, null);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.MANUAL_REVIEW.getValue(), result.status());
        assertEquals(ReviewReasonCode.TECH_FAILURE.getValue(), result.reviewReasonType());
        assertTrue(result.rejectionReason().contains("БКИ"));
    }

    @Test
    @DisplayName("Технический сбой обоих реестров (ФНС и БКИ) переводит в MANUAL_REVIEW с кодом TECH_FAILURE")
    void testBothServicesUnavailableLeadsToManualReview() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);

        BorrowerDossier dossier = new BorrowerDossier(user, app, null, null);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.MANUAL_REVIEW.getValue(), result.status());
        assertEquals(ReviewReasonCode.TECH_FAILURE.getValue(), result.reviewReasonType());
        assertTrue(result.rejectionReason().contains("ФНС БКИ"));
    }

    @Test
    @DisplayName("Превышение ПДН (> 80%) при непосильной кредитной нагрузке приводит к отказу REJECTED")
    void testExcessivePdnRejection() {
        UserEntity user = createGoodUser();
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 750, 350_000L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());
        ApplicationEntity app = createApplication(1_000_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertTrue(result.rejectionReason().contains("ПДН"));
    }

    @Test
    @DisplayName("Низкий скоринговый балл (< 50) приводит к отказу REJECTED")
    void testLowScoringScoreRejection() {
        // Оборот < 300к (5) + стаж 2 мес (3) = 8 баллов
        UserEntity lowUser = new UserEntity(102L, "client_low", "CLIENT", "ООО", "7702000000", 100_000L, 1_200_000L, 5_000L, 2, OffsetDateTime.now());
        ApplicationEntity app = createApplication(150_000L, 12);
        // ФНС возраст 3 мес (0 баллов)
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7702000000", "ООО", "ACTIVE", false, 3, OffsetDateTime.now());
        // БКИ скор 450 (0 баллов)
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7702000000", 450, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(lowUser, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertEquals(8, result.scoringScore());
        assertTrue(result.rejectionReason().contains("ниже проходного порога"));
    }

    @Test
    @DisplayName("Пограничный скоринговый балл (50-64) переводит в MANUAL_REVIEW с кодом BORDERLINE_SCORE")
    void testBorderlineScoreLeadsToManualReview() {
        // Оборот 500к (12) + стаж 15 мес (15) = 27 баллов
        UserEntity midUser = new UserEntity(103L, "client_mid", "CLIENT", "ООО", "7703000000", 500_000L, 6_000_000L, 50_000L, 15, OffsetDateTime.now());
        ApplicationEntity app = createApplication(300_000L, 12);
        // ФНС возраст 15 мес (20 баллов)
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7703000000", "ООО", "ACTIVE", false, 15, OffsetDateTime.now());
        // БКИ скор 550 (15 баллов). Итого = 27 + 20 + 15 = 62 балла (граница 50-64)
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7703000000", 550, 10_000L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(midUser, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.MANUAL_REVIEW.getValue(), result.status());
        assertEquals(ReviewReasonCode.BORDERLINE_SCORE.getValue(), result.reviewReasonType());
        assertEquals(62, result.scoringScore());
        assertTrue(result.rejectionReason().contains("Пограничный скоринговый балл"));
    }

    @Test
    @DisplayName("Контр-оффер: одобренная сумма урезается до максимально допустимого лимита")
    void testCounterOfferApprovedAmountCappedByLimit() {
        // Доход 375к/мес, выручка 6 млн/год -> лимит 30% = 1.8 млн руб.
        UserEntity user = new UserEntity(104L, "client_rich", "CLIENT", "ООО", "7704000000", 1_500_000L, 6_000_000L, 200_000L, 24, OffsetDateTime.now());
        // Клиент запросил 2.5 млн руб. (ПДН = 62% < 80%, проходит по ПДН)
        ApplicationEntity app = createApplication(2_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7704000000", "ООО", "ACTIVE", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7704000000", 750, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.APPROVED.getValue(), result.status());
        // Одобренная сумма должна быть урезана до лимита (1.8 млн), а не 2.5 млн
        assertEquals(1_800_000L, result.approvedAmount());
        assertEquals(1_800_000L, result.maxLimitRub());
    }

    @Test
    @DisplayName("Отказ, если рассчитанный лимит ниже минимальной суммы кредита 100 000 руб.")
    void testLimitBelowMinAmountRejection() {
        // Доход 125к/мес. Текущий платеж в БКИ 60 000 руб.
        // Допустимый платеж при ПДН 50%: 125 000 * 0.5 - 60 000 = 2 500 руб.
        // Лимит по ПДН: 2 500 / 0.0931 = 26 849 руб. (< 100 000 руб.)
        UserEntity user = new UserEntity(105L, "client_tiny", "CLIENT", "ООО", "7705000000", 500_000L, 6_000_000L, 10_000L, 24, OffsetDateTime.now());
        // Запрос 100 000 руб. Платеж 9 311 руб. ПДН = (60 000 + 9 311) / 125 000 = 55.4% (< 80%, проходит)
        ApplicationEntity app = createApplication(100_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7705000000", "ООО", "ACTIVE", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7705000000", 750, 60_000L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertTrue(result.rejectionReason().contains("ниже минимальной суммы"));
    }

    @Test
    @DisplayName("Граничный балл скоринга: максимальный балл в зоне отказа (48 баллов) приводит к REJECTED")
    void testBoundaryScore48Rejection() {
        // Оборот 1 млн (20) + стаж 2 мес (3) = 23 балла
        UserEntity user = new UserEntity(106L, "client_48", "CLIENT", "ООО", "7706000000", 1_000_000L, 12_000_000L, 20_000L, 2, OffsetDateTime.now());
        ApplicationEntity app = createApplication(150_000L, 12);
        // ФНС возраст 3 мес (0 баллов)
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7706000000", "ООО", "ACTIVE", false, 3, OffsetDateTime.now());
        // БКИ скор 600 (25 баллов). Итого = 23 + 0 + 25 = 48 баллов (< 50)
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7706000000", 600, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertEquals(48, result.scoringScore());
        assertTrue(result.rejectionReason().contains("48/100"));
    }

    @Test
    @DisplayName("Граничный балл скоринга: ровно 50 баллов переводит в MANUAL_REVIEW с BORDERLINE_SCORE")
    void testBoundaryScore50ManualReview() {
        // Оборот 500к (12) + стаж 2 мес (3) = 15 баллов
        UserEntity user = new UserEntity(107L, "client_50", "CLIENT", "ООО", "7707000000", 500_000L, 6_000_000L, 20_000L, 2, OffsetDateTime.now());
        ApplicationEntity app = createApplication(200_000L, 12);
        // ФНС возраст 12 мес (20 баллов)
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7707000000", "ООО", "ACTIVE", false, 12, OffsetDateTime.now());
        // БКИ скор 550 (15 баллов). Итого = 15 + 20 + 15 = 50 баллов ровно
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7707000000", 550, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.MANUAL_REVIEW.getValue(), result.status());
        assertEquals(ReviewReasonCode.BORDERLINE_SCORE.getValue(), result.reviewReasonType());
        assertEquals(50, result.scoringScore());
        assertTrue(result.rejectionReason().contains("50/100"));
    }

    @Test
    @DisplayName("Граничный балл скоринга: максимальный балл в зоне ручной проверки (63 балла) переводит в MANUAL_REVIEW")
    void testBoundaryScore63ManualReview() {
        // Оборот 200к (5) + стаж 2 мес (3) = 8 баллов
        UserEntity user = new UserEntity(108L, "client_63", "CLIENT", "ООО", "7708000000", 200_000L, 2_400_000L, 20_000L, 2, OffsetDateTime.now());
        ApplicationEntity app = createApplication(200_000L, 12);
        // ФНС возраст 24 мес (30 баллов)
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7708000000", "ООО", "ACTIVE", false, 24, OffsetDateTime.now());
        // БКИ скор 600 (25 баллов). Итого = 8 + 30 + 25 = 63 балла (< 65)
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7708000000", 600, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.MANUAL_REVIEW.getValue(), result.status());
        assertEquals(ReviewReasonCode.BORDERLINE_SCORE.getValue(), result.reviewReasonType());
        assertEquals(63, result.scoringScore());
        assertTrue(result.rejectionReason().contains("63/100"));
    }

    @Test
    @DisplayName("Граничный балл скоринга: ровно 65 баллов одобряется, а ставка отсекается до максимума 23.5%")
    void testBoundaryScore65ApprovalAndRateClamping() {
        // Оборот 200к (5) + стаж 12 мес (15) = 20 баллов
        UserEntity user = new UserEntity(109L, "client_65", "CLIENT", "ООО", "7709000000", 200_000L, 2_400_000L, 20_000L, 12, OffsetDateTime.now());
        ApplicationEntity app = createApplication(200_000L, 12);
        // ФНС возраст 24 мес (30 баллов)
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7709000000", "ООО", "ACTIVE", false, 24, OffsetDateTime.now());
        // БКИ скор 550 (15 баллов). Итого = 20 + 30 + 15 = 65 баллов ровно
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7709000000", 550, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.APPROVED.getValue(), result.status());
        assertEquals(65, result.scoringScore());
        // Расчетная ставка 24.0% отсекается Math.clamp(rate, 16.5, 23.5) ровно до 23.5%
        assertEquals(23.5, result.approvedRate());
        assertEquals(200_000L, result.approvedAmount());
    }

    @Test
    @DisplayName("Граничное значение просрочки: ровно 30 дней (<= 30) не является стоп-фактором")
    void testBoundaryOverdue30DaysNotRejected() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7701234567", "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());
        // Просрочка ровно 30 дней (порог отказа: > 30)
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 750, 0L, 30, 0, "PAST_DUE", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        // Стоп-фактор не сработал
        assertEquals(ApplicationStatus.APPROVED.getValue(), result.status());
    }

    @Test
    @DisplayName("Сбой только сервиса ФНС при успешном ответе БКИ переводит в MANUAL_REVIEW с TECH_FAILURE")
    void testFnsUnavailableLeadsToManualReview() {
        UserEntity user = createGoodUser();
        ApplicationEntity app = createApplication(1_500_000L, 12);
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7701234567", 750, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, null, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.MANUAL_REVIEW.getValue(), result.status());
        assertEquals(ReviewReasonCode.TECH_FAILURE.getValue(), result.reviewReasonType());
        assertTrue(result.rejectionReason().contains("ФНС"));
    }

    @Test
    @DisplayName("Нулевой оборот заемщика: ПДН = 100% приводит к отказу REJECTED")
    void testZeroTurnoverPdn100Rejection() {
        UserEntity user = new UserEntity(110L, "client_zero", "CLIENT", "ООО", "7710000000", 0L, 0L, 0L, 12, OffsetDateTime.now());
        ApplicationEntity app = createApplication(200_000L, 12);
        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, 1L, "7710000000", "ООО", "ACTIVE", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, 1L, "7710000000", 750, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        BorrowerDossier dossier = new BorrowerDossier(user, app, fns, bki);
        DecisionResult result = decisionEngine.evaluate(dossier);

        assertEquals(ApplicationStatus.REJECTED.getValue(), result.status());
        assertEquals(100.0, result.calculatedPdnPercent());
        assertTrue(result.rejectionReason().contains("Превышен показатель предельной долговой нагрузки"));
    }
}
