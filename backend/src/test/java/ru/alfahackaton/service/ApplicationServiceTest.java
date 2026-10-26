package ru.alfahackaton.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import ru.alfahackaton.dto.ApplicationDetailsResponse;
import ru.alfahackaton.dto.ApplicationPageResponse;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.dto.LoanPurpose;
import ru.alfahackaton.dto.ReviewReasonCode;
import ru.alfahackaton.dto.SignApplicationRequest;
import ru.alfahackaton.event.ApplicationCreatedEvent;
import ru.alfahackaton.mapper.ApplicationMapper;
import ru.alfahackaton.persistance.entity.ApplicationBkiSnapshotEntity;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.ApplicationFnsSnapshotEntity;
import ru.alfahackaton.persistance.entity.UserEntity;
import ru.alfahackaton.persistance.repository.ApplicationBkiSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationFnsSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplicationServiceTest {

    private ApplicationRepository applicationRepository;
    private UserRepository userRepository;
    private ApplicationFnsSnapshotRepository fnsSnapshotRepository;
    private ApplicationBkiSnapshotRepository bkiSnapshotRepository;
    private FnsEnrichmentService fnsEnrichmentService;
    private BkiEnrichmentService bkiEnrichmentService;
    private DecisionEngine decisionEngine;
    private PaymentScheduleService paymentScheduleService;
    private ApplicationMapper mapper;
    private ApplicationEventPublisher eventPublisher;
    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationRepository = Mockito.mock(ApplicationRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        fnsSnapshotRepository = Mockito.mock(ApplicationFnsSnapshotRepository.class);
        bkiSnapshotRepository = Mockito.mock(ApplicationBkiSnapshotRepository.class);
        fnsEnrichmentService = Mockito.mock(FnsEnrichmentService.class);
        bkiEnrichmentService = Mockito.mock(BkiEnrichmentService.class);
        decisionEngine = new DecisionEngine();
        paymentScheduleService = new PaymentScheduleService();
        mapper = Mockito.mock(ApplicationMapper.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

        applicationService = new ApplicationService(
                applicationRepository,
                userRepository,
                fnsSnapshotRepository,
                bkiSnapshotRepository,
                fnsEnrichmentService,
                bkiEnrichmentService,
                decisionEngine,
                paymentScheduleService,
                mapper,
                eventPublisher
        );
    }

    @Test
    @DisplayName("При создании заявки публикуется ApplicationCreatedEvent и заявка сохраняется со статусом PROCESSING")
    void testCreateApplicationPublishesEvent() {
        Long userId = 101L;
        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", "7701234567",
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ApplicationEntity savedEntity = new ApplicationEntity(
                42L, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", "7701234567"
        );
        when(applicationRepository.save(any(ApplicationEntity.class))).thenReturn(savedEntity);

        ApplicationResponse expectedResponse = new ApplicationResponse();
        expectedResponse.setId(42L);
        expectedResponse.setStatus(ApplicationStatus.PROCESSING);
        when(mapper.toDto(savedEntity)).thenReturn(expectedResponse);

        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setAmount(1_000_000L);
        request.setTermMonths(12);
        request.setPurpose(LoanPurpose.WORKING_CAPITAL);

        ApplicationResponse actualResponse = applicationService.createApplication(request, userId);

        assertNotNull(actualResponse);
        assertEquals(ApplicationStatus.PROCESSING, actualResponse.getStatus());

        ArgumentCaptor<ApplicationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ApplicationCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(42L, eventCaptor.getValue().applicationId());
        assertEquals("7701234567", eventCaptor.getValue().inn());
    }

    @Test
    @DisplayName("processApplication: Structured Concurrency опрашивает ФНС/БКИ и сохраняет точные значения решения")
    void testProcessApplicationEnrichesAndUpdatesDecisionStrictValues() {
        Long appId = 42L;
        Long userId = 101L;
        String inn = "7701234567";

        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", inn,
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );
        ApplicationEntity app = new ApplicationEntity(
                appId, userId, 1_500_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", inn
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, appId, inn, "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, appId, inn, 750, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());

        when(fnsEnrichmentService.enrich(appId, inn)).thenReturn(fns);
        when(bkiEnrichmentService.enrich(appId, inn)).thenReturn(bki);
        when(fnsSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.of(fns));
        when(bkiSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.of(bki));

        applicationService.processApplication(appId);

        verify(fnsEnrichmentService).enrich(appId, inn);
        verify(bkiEnrichmentService).enrich(appId, inn);

        // Строгая проверка конкретных вычисленных скорингом значений:
        // 100 баллов, ставка 18.8%, одобрено 1 500 000 руб., ПДН 37.2%, лимит 2 013 666 руб.
        ArgumentCaptor<Double> pdnCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Long> limitCaptor = ArgumentCaptor.forClass(Long.class);

        verify(applicationRepository).updateDecision(
                eq(appId),
                eq(ApplicationStatus.APPROVED.getValue()),
                isNull(),
                isNull(),
                eq(100),
                pdnCaptor.capture(),
                limitCaptor.capture(),
                eq(1_500_000L),
                eq(18.8)
        );
        assertEquals(37.2, pdnCaptor.getValue());
        assertEquals(2_013_666L, limitCaptor.getValue());
    }

    @Test
    @DisplayName("Structured Concurrency: при падении ФНС заявка изолированно сохраняет БКИ и уходит в TECH_FAILURE")
    void testStructuredConcurrencyPartialFailureLeadsToTechFailure() {
        Long appId = 43L;
        Long userId = 101L;
        String inn = "7701234567";

        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", inn,
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );
        ApplicationEntity app = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", inn
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // ФНС падает с RuntimeException
        when(fnsEnrichmentService.enrich(appId, inn)).thenThrow(new RuntimeException("ФНС шлюз 503"));
        // БКИ успешно отрабатывает
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, appId, inn, 750, 50_000L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());
        when(bkiEnrichmentService.enrich(appId, inn)).thenReturn(bki);

        when(fnsSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.empty());
        when(bkiSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.of(bki));

        applicationService.processApplication(appId);

        // Проверяем перевод в MANUAL_REVIEW с кодом TECH_FAILURE и содержанием имени упавшего сервиса
        verify(applicationRepository).updateDecision(
                eq(appId),
                eq(ApplicationStatus.MANUAL_REVIEW.getValue()),
                argThat(reason -> reason != null && reason.contains("ФНС")),
                eq(ReviewReasonCode.TECH_FAILURE.getValue()),
                isNull(), isNull(), isNull(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("Клиент успешно подписывает одобренную заявку (переход в SIGNED)")
    void testSignApplicationSuccess() {
        Long appId = 42L;
        Long userId = 101L;

        ApplicationEntity approvedApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null, null,
                85, 34.0, 3_000_000L, 1_000_000L, 18.5,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", "7701234567"
        );
        ApplicationEntity signedApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.SIGNED.getValue(), null, null,
                85, 34.0, 3_000_000L, 1_000_000L, 18.5,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", "7701234567"
        );

        when(applicationRepository.findByIdAndUserId(appId, userId))
                .thenReturn(Optional.of(approvedApp))
                .thenReturn(Optional.of(signedApp));
        when(applicationRepository.markAsSignedIfApproved(appId)).thenReturn(1);

        ApplicationResponse responseDto = new ApplicationResponse();
        responseDto.setId(appId);
        responseDto.setStatus(ApplicationStatus.SIGNED);
        when(mapper.toDto(signedApp)).thenReturn(responseDto);

        SignApplicationRequest request = new SignApplicationRequest();
        request.setConfirmTerms(true);
        request.setSmsCode("1234");

        ApplicationResponse result = applicationService.signApplication(appId, request, userId);

        assertEquals(ApplicationStatus.SIGNED, result.getStatus());
        verify(applicationRepository).markAsSignedIfApproved(appId);
    }

    @Test
    @DisplayName("Попытка подписать заявку не в статусе APPROVED бросает IllegalStateException")
    void testSignApplicationNotApprovedThrowsException() {
        Long appId = 42L;
        Long userId = 101L;

        ApplicationEntity processingApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", "7701234567"
        );

        when(applicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(processingApp));

        SignApplicationRequest request = new SignApplicationRequest();
        request.setConfirmTerms(true);
        request.setSmsCode("1234");

        assertThrows(IllegalStateException.class, () -> applicationService.signApplication(appId, request, userId));
    }

    @Test
    @DisplayName("Валидация подписания: отказ от согласия с условиями (confirmTerms=false) бросает ошибку")
    void testSignApplicationWithoutTermsThrowsException() {
        Long appId = 42L;
        Long userId = 101L;

        ApplicationEntity approvedApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", "7701234567"
        );
        when(applicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(approvedApp));

        SignApplicationRequest request = new SignApplicationRequest();
        request.setConfirmTerms(false);
        request.setSmsCode("1234");

        assertThrows(IllegalArgumentException.class, () -> applicationService.signApplication(appId, request, userId));
    }

    @Test
    @DisplayName("Валидация подписания: пустой SMS-код бросает ошибку")
    void testSignApplicationEmptySmsCodeThrowsException() {
        Long appId = 42L;
        Long userId = 101L;

        ApplicationEntity approvedApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", "7701234567"
        );
        when(applicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(approvedApp));

        SignApplicationRequest request = new SignApplicationRequest();
        request.setConfirmTerms(true);
        request.setSmsCode("   ");

        assertThrows(IllegalArgumentException.class, () -> applicationService.signApplication(appId, request, userId));
    }

    @Test
    @DisplayName("retryApplication: успешный перезапуск конвейера при TECH_FAILURE")
    void testRetryApplicationSuccess() {
        Long appId = 44L;
        Long userId = 101L;
        String inn = "7701234567";

        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", inn,
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );
        ApplicationEntity techFailApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.MANUAL_REVIEW.getValue(), "Сбой ФНС",
                ReviewReasonCode.TECH_FAILURE.getValue(),
                null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", inn
        );
        ApplicationEntity processingApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", inn
        );

        when(applicationRepository.findWithUserById(appId))
                .thenReturn(Optional.of(techFailApp))
                .thenReturn(Optional.of(processingApp))
                .thenReturn(Optional.of(techFailApp));
        when(applicationRepository.markAsProcessingIfEligibleForRetry(appId)).thenReturn(1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ApplicationResponse responseDto = new ApplicationResponse();
        responseDto.setId(appId);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(responseDto);

        ApplicationResponse result = applicationService.retryApplication(appId);

        assertNotNull(result);
        verify(applicationRepository).markAsProcessingIfEligibleForRetry(appId);
    }

    @Test
    @DisplayName("retryApplication: попытка перезапуска заявки со статусом APPROVED бросает IllegalStateException")
    void testRetryApplicationInvalidStatusThrowsException() {
        Long appId = 45L;
        ApplicationEntity approvedApp = new ApplicationEntity(
                appId, 101L, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", "7701234567"
        );
        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(approvedApp));

        assertThrows(IllegalStateException.class, () -> applicationService.retryApplication(appId));
    }

    @Test
    @DisplayName("getEmployeeApplicationDetails: возвращает полное досье с флагом canRetry, снимками и графиком")
    void testGetEmployeeApplicationDetails() {
        Long appId = 46L;
        Long userId = 101L;
        String inn = "7701234567";

        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", inn,
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );
        ApplicationEntity app = new ApplicationEntity(
                appId, userId, 1_500_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.MANUAL_REVIEW.getValue(), "Сбой ФНС",
                ReviewReasonCode.TECH_FAILURE.getValue(),
                null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", inn
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, appId, inn, "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());
        ApplicationBkiSnapshotEntity bki = new ApplicationBkiSnapshotEntity(1L, appId, inn, 750, 0L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());
        when(fnsSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.of(fns));
        when(bkiSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.of(bki));

        when(mapper.mapPurpose("WORKING_CAPITAL")).thenReturn(LoanPurpose.WORKING_CAPITAL);
        when(mapper.mapStatus(ApplicationStatus.MANUAL_REVIEW.getValue())).thenReturn(ApplicationStatus.MANUAL_REVIEW);

        ApplicationDetailsResponse details = applicationService.getEmployeeApplicationDetails(appId);

        assertNotNull(details);
        assertEquals(appId, details.getId());
        assertEquals("7701234567", details.getInn());
        assertEquals(ReviewReasonCode.TECH_FAILURE, details.getReviewReasonCode());
        // Так как статус MANUAL_REVIEW и причина TECH_FAILURE, кнопка canRetry обязана быть true!
        assertTrue(details.getCanRetry());
        assertNotNull(details.getUser());
        assertEquals("client_good", details.getUser().getUsername());
        assertNotNull(details.getFns());
        assertEquals("ACTIVE", details.getFns().getStateStatus());
        assertNotNull(details.getBki());
        assertEquals(750, details.getBki().getBkiScore());
        assertEquals("CURRENT (0 DPD)", details.getBki().getRiskBucket());
    }

    @Test
    @DisplayName("Пагинированный реестр возвращает страницу и метаданные пагинации")
    void testGetEmployeeApplicationsPaged() {
        ApplicationEntity app1 = new ApplicationEntity(1L, 101L, 500_000L, 12, "WORKING_CAPITAL", "PROCESSING", null, OffsetDateTime.now(), OffsetDateTime.now(), "Ромашка", "7701234567");
        when(applicationRepository.findAllPaged(10, 0L)).thenReturn(List.of(app1));
        when(applicationRepository.count()).thenReturn(25L);

        ApplicationResponse resp1 = new ApplicationResponse();
        resp1.setId(1L);
        when(mapper.toDto(app1)).thenReturn(resp1);

        ApplicationPageResponse page = applicationService.getEmployeeApplicationsPaged(null, 0, 10);

        assertEquals(1, page.getItems().size());
        assertEquals(0, page.getPage());
        assertEquals(10, page.getSize());
        assertEquals(25L, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
    }

    @Test
    @DisplayName("Structured Concurrency: сбой БКИ при успехе ФНС изолированно уводит заявку в TECH_FAILURE")
    void testStructuredConcurrencyBkiFailureLeadsToTechFailure() {
        Long appId = 44L;
        Long userId = 101L;
        String inn = "7701234567";

        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", inn,
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );
        ApplicationEntity app = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", inn
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ApplicationFnsSnapshotEntity fns = new ApplicationFnsSnapshotEntity(1L, appId, inn, "ООО \"Ромашка\"", "ACTIVE", false, 36, OffsetDateTime.now());
        when(fnsEnrichmentService.enrich(appId, inn)).thenReturn(fns);
        // БКИ падает
        when(bkiEnrichmentService.enrich(appId, inn)).thenThrow(new RuntimeException("БКИ сервис таймаут"));

        when(fnsSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.of(fns));
        when(bkiSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.empty());

        applicationService.processApplication(appId);

        verify(applicationRepository).updateDecision(
                eq(appId),
                eq(ApplicationStatus.MANUAL_REVIEW.getValue()),
                argThat(reason -> reason != null && reason.contains("БКИ")),
                eq(ReviewReasonCode.TECH_FAILURE.getValue()),
                isNull(), isNull(), isNull(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("Structured Concurrency: одновременный сбой ФНС и БКИ фиксирует общий техсбой")
    void testStructuredConcurrencyBothServicesFailureLeadsToTechFailure() {
        Long appId = 45L;
        Long userId = 101L;
        String inn = "7701234567";

        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", inn,
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );
        ApplicationEntity app = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", inn
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(fnsEnrichmentService.enrich(appId, inn)).thenThrow(new RuntimeException("ФНС недоступна"));
        when(bkiEnrichmentService.enrich(appId, inn)).thenThrow(new RuntimeException("БКИ недоступно"));

        when(fnsSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.empty());
        when(bkiSnapshotRepository.findLatestByApplicationId(appId)).thenReturn(Optional.empty());

        applicationService.processApplication(appId);

        verify(applicationRepository).updateDecision(
                eq(appId),
                eq(ApplicationStatus.MANUAL_REVIEW.getValue()),
                argThat(reason -> reason != null && reason.contains("ФНС") && reason.contains("БКИ")),
                eq(ReviewReasonCode.TECH_FAILURE.getValue()),
                isNull(), isNull(), isNull(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("Идемпотентность скоринга: если статус не PROCESSING, повторный скоринг игнорируется")
    void testProcessApplicationAlreadyProcessedIsIdempotent() {
        Long appId = 46L;
        ApplicationEntity app = new ApplicationEntity(
                appId, 101L, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null,
                OffsetDateTime.now(), OffsetDateTime.now(), "Ромашка", "7701234567"
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));

        applicationService.processApplication(appId);

        verifyNoInteractions(fnsEnrichmentService);
        verifyNoInteractions(bkiEnrichmentService);
        verify(applicationRepository, never()).updateDecision(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Пагинация с фильтром по статусу вызывает findByStatusPaged и countByStatus")
    void testGetEmployeeApplicationsPagedWithStatusFilter() {
        ApplicationEntity app = new ApplicationEntity(2L, 101L, 300_000L, 6, "WORKING_CAPITAL", "MANUAL_REVIEW", null, OffsetDateTime.now(), OffsetDateTime.now(), "ИП", "7702000000");
        when(applicationRepository.findByStatusPaged("MANUAL_REVIEW", 10, 0L)).thenReturn(List.of(app));
        when(applicationRepository.countByStatus("MANUAL_REVIEW")).thenReturn(1L);

        ApplicationResponse resp = new ApplicationResponse();
        resp.setId(2L);
        when(mapper.toDto(app)).thenReturn(resp);

        ApplicationPageResponse page = applicationService.getEmployeeApplicationsPaged(ApplicationStatus.MANUAL_REVIEW, 0, 10);

        assertEquals(1, page.getItems().size());
        assertEquals(1L, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
        verify(applicationRepository).findByStatusPaged("MANUAL_REVIEW", 10, 0L);
        verify(applicationRepository).countByStatus("MANUAL_REVIEW");
        verify(applicationRepository, never()).findAllPaged(anyInt(), anyLong());
    }

    @Test
    @DisplayName("Кредитное досье для BORDERLINE_SCORE: canRetry строго равен false")
    void testGetEmployeeApplicationDetailsBorderlineScoreCanRetryFalse() {
        Long appId = 47L;
        Long userId = 101L;
        String inn = "7701234567";

        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", inn,
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );
        ApplicationEntity app = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.MANUAL_REVIEW.getValue(), "Пограничный балл 55/100",
                ReviewReasonCode.BORDERLINE_SCORE.getValue(), 55, 45.0, 1_500_000L, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), "ООО \"Ромашка\"", inn
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapper.mapPurpose("WORKING_CAPITAL")).thenReturn(LoanPurpose.WORKING_CAPITAL);
        when(mapper.mapStatus(ApplicationStatus.MANUAL_REVIEW.getValue())).thenReturn(ApplicationStatus.MANUAL_REVIEW);

        ApplicationDetailsResponse details = applicationService.getEmployeeApplicationDetails(appId);

        assertNotNull(details);
        assertEquals(ReviewReasonCode.BORDERLINE_SCORE, details.getReviewReasonCode());
        // Для BORDERLINE_SCORE кнопка перезапуска ЗАПРЕЩЕНА (строго false)
        assertFalse(details.getCanRetry());
    }

    @Test
    @DisplayName("Перезапуск заявки при BORDERLINE_SCORE выбрасывает IllegalStateException")
    void testRetryApplicationBorderlineScoreThrowsIllegalStateException() {
        Long appId = 48L;
        ApplicationEntity app = new ApplicationEntity(
                appId, 101L, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.MANUAL_REVIEW.getValue(), "Пограничный балл",
                ReviewReasonCode.BORDERLINE_SCORE.getValue(), 55, 40.0, 1_500_000L, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), "Ромашка", "7701234567"
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> applicationService.retryApplication(appId));

        assertTrue(ex.getMessage().contains("техническом сбое"));
    }

    @Test
    @DisplayName("Клиент получает заявку по ID с графиком платежей при статусе APPROVED")
    void testGetClientApplicationApprovedWithSchedule() {
        Long appId = 49L;
        Long userId = 101L;
        ApplicationEntity app = new ApplicationEntity(
                appId, userId, 1_500_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null, null,
                100, 36.8, 2_000_000L, 1_500_000L, 18.8,
                OffsetDateTime.now(), OffsetDateTime.now(), "Ромашка", "7701234567"
        );

        when(applicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(app));

        ApplicationResponse dto = new ApplicationResponse();
        dto.setId(appId);
        dto.setStatus(ApplicationStatus.APPROVED);
        when(mapper.toDto(app)).thenReturn(dto);

        ApplicationResponse response = applicationService.getClientApplication(appId, userId);

        assertNotNull(response);
        assertEquals(appId, response.getId());
        // Проверяем, что для APPROVED сгенерирован и прикреплен график платежей
        assertNotNull(response.getSchedule());
        assertEquals(12, response.getSchedule().getItems().size());
    }

    @Test
    @DisplayName("Клиент запрашивает несуществующую или чужую заявку: выбрасывается IllegalArgumentException")
    void testGetClientApplicationNotFoundThrowsException() {
        when(applicationRepository.findByIdAndUserId(999L, 101L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> applicationService.getClientApplication(999L, 101L));
    }

    @Test
    @DisplayName("Клиент получает список всех своих заявок")
    void testGetClientApplicationsList() {
        Long userId = 101L;
        ApplicationEntity app1 = new ApplicationEntity(1L, userId, 500_000L, 12, "WORKING_CAPITAL", "PROCESSING", null, OffsetDateTime.now(), OffsetDateTime.now(), "Ромашка", "7701234567");
        ApplicationEntity app2 = new ApplicationEntity(2L, userId, 1_000_000L, 24, "EQUIPMENT", "REJECTED", "Стоп-фактор", OffsetDateTime.now(), OffsetDateTime.now(), "Ромашка", "7701234567");

        when(applicationRepository.findByUserId(userId)).thenReturn(List.of(app1, app2));

        ApplicationResponse resp1 = new ApplicationResponse();
        resp1.setId(1L);
        ApplicationResponse resp2 = new ApplicationResponse();
        resp2.setId(2L);

        when(mapper.toDto(app1)).thenReturn(resp1);
        when(mapper.toDto(app2)).thenReturn(resp2);

        List<ApplicationResponse> list = applicationService.getClientApplications(userId);

        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).getId());
        assertEquals(2L, list.get(1).getId());
    }

    @Test
    @DisplayName("processApplication: задачи обогащения ФНС и БКИ исполняются в виртуальных потоках")
    void testProcessApplicationRunsOnVirtualThreads() {
        Long appId = 42L;
        Long userId = 101L;
        String inn = "7701234567";

        ApplicationEntity app = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null, OffsetDateTime.now(), OffsetDateTime.now(),
                "Ромашка", inn
        );
        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", inn,
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AtomicBoolean fnsWasVirtual = new AtomicBoolean(false);
        AtomicBoolean bkiWasVirtual = new AtomicBoolean(false);

        when(fnsEnrichmentService.enrich(eq(appId), eq(inn))).thenAnswer(invocation -> {
            fnsWasVirtual.set(Thread.currentThread().isVirtual());
            return new ApplicationFnsSnapshotEntity(1L, appId, inn, "Ромашка", "ACTIVE", false, 36, OffsetDateTime.now());
        });

        when(bkiEnrichmentService.enrich(eq(appId), eq(inn))).thenAnswer(invocation -> {
            bkiWasVirtual.set(Thread.currentThread().isVirtual());
            return new ApplicationBkiSnapshotEntity(1L, appId, inn, 700, 20_000L, 0, 0, "CURRENT (0 DPD)", OffsetDateTime.now());
        });

        applicationService.processApplication(appId);

        assertTrue(fnsWasVirtual.get(), "Обогащение ФНС должно выполняться в виртуальном потоке");
        assertTrue(bkiWasVirtual.get(), "Обогащение БКИ должно выполняться в виртуальном потоке");
    }

    @Test
    @DisplayName("Гонка данных: одновременное подписание заявки двумя потоками (должен преуспеть только один)")
    void testConcurrentSignApplicationRaceCondition() throws Exception {
        Long appId = 42L;
        Long userId = 101L;

        ApplicationEntity approvedApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null, OffsetDateTime.now(), OffsetDateTime.now(),
                "Ромашка", "7701234567"
        );

        when(applicationRepository.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(approvedApp));
        when(applicationRepository.markAsSignedIfApproved(appId))
                .thenReturn(1)
                .thenReturn(0);
        when(mapper.toDto(any())).thenReturn(new ApplicationResponse().status(ApplicationStatus.SIGNED));

        SignApplicationRequest request = new SignApplicationRequest();
        request.setConfirmTerms(true);
        request.setSmsCode("1234");

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable signTask = () -> {
            try {
                startLatch.await();
                applicationService.signApplication(appId, request, userId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Thread t1 = new Thread(signTask);
        Thread t2 = new Thread(signTask);
        t1.start();
        t2.start();
        startLatch.countDown();
        t1.join();
        t2.join();

        assertEquals(1, successCount.get(), "Ровно один запрос должен успешно подписать заявку");
        assertEquals(1, failureCount.get(), "Конкурирующий запрос должен быть отклонен");
    }

    @Test
    @DisplayName("Гонка данных: одновременный вызов retryApplication двумя потоками (должен запуститься ровно один)")
    void testConcurrentRetryApplicationRaceCondition() throws Exception {
        Long appId = 42L;
        Long userId = 101L;

        ApplicationEntity reviewApp = new ApplicationEntity(
                appId, userId, 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.MANUAL_REVIEW.getValue(), "Сбой сервиса", ReviewReasonCode.TECH_FAILURE.getValue(),
                null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(),
                "Ромашка", "7701234567"
        );
        UserEntity user = new UserEntity(
                userId, "client_good", "CLIENT", "ООО \"Ромашка\"", "7701234567",
                1_500_000L, 18_000_000L, 350_000L, 24, OffsetDateTime.now()
        );

        when(applicationRepository.findWithUserById(appId)).thenReturn(Optional.of(reviewApp));
        when(applicationRepository.markAsProcessingIfEligibleForRetry(appId))
                .thenReturn(1)
                .thenReturn(0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapper.toDto(any())).thenReturn(new ApplicationResponse().status(ApplicationStatus.APPROVED));

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable retryTask = () -> {
            try {
                startLatch.await();
                applicationService.retryApplication(appId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Thread t1 = new Thread(retryTask);
        Thread t2 = new Thread(retryTask);
        t1.start();
        t2.start();
        startLatch.countDown();
        t1.join();
        t2.join();

        assertEquals(1, successCount.get(), "Ровно один вызов retry должен преуспеть");
        assertEquals(1, failureCount.get(), "Второй одновременный вызов retry должен получить отказ");
    }
}
