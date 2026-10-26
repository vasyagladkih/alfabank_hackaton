package ru.alfahackaton.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alfahackaton.api.models.BorrowerDossier;
import ru.alfahackaton.api.models.DecisionResult;
import ru.alfahackaton.dto.ApplicationDetailsResponse;
import ru.alfahackaton.dto.ApplicationPageResponse;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.BkiSnapshotDto;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.dto.FnsSnapshotDto;
import ru.alfahackaton.dto.ManualApproveRequest;
import ru.alfahackaton.dto.ManualRejectRequest;
import ru.alfahackaton.dto.ReviewReasonCode;
import ru.alfahackaton.dto.ScoringMetricsDto;
import ru.alfahackaton.dto.SignApplicationRequest;
import ru.alfahackaton.dto.UserProfile;
import ru.alfahackaton.dto.UserRole;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Основной сервис кредитного конвейера.
 * Обеспечивает оркестрацию создания заявок, параллельного обогащения через Virtual Threads,
 * скоринга, подписания и предоставления данных клиенту и сотрудникам банка.
 */
@Slf4j
@Service
@AllArgsConstructor
public class ApplicationService {

    private static final ExecutorService VT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ApplicationFnsSnapshotRepository fnsSnapshotRepository;
    private final ApplicationBkiSnapshotRepository bkiSnapshotRepository;
    private final FnsEnrichmentService fnsEnrichmentService;
    private final BkiEnrichmentService bkiEnrichmentService;
    private final DecisionEngine decisionEngine;
    private final PaymentScheduleService paymentScheduleService;
    private final ApplicationMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, Long currentUserId) {

        UserEntity user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь #" + currentUserId + " не найден"));

        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());

        ApplicationEntity entity = new ApplicationEntity(
                null, currentUserId, request.getAmount(),
                request.getTermMonths(), request.getPurpose().getValue(),
                ApplicationStatus.PROCESSING.getValue(),
                null, now, now,
                user.companyName(), user.inn()
        );

        ApplicationEntity saved = applicationRepository.save(entity);
        eventPublisher.publishEvent(new ApplicationCreatedEvent(saved.id(), user.inn()));

        ApplicationResponse response = mapper.toDto(saved);
        if (response.getCompanyName() == null) response.setCompanyName(user.companyName());
        if (response.getInn() == null) response.setInn(user.inn());
        return response;
    }

    public void processApplication(Long applicationId) {

        ApplicationEntity application = applicationRepository.findWithUserById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        if (!ApplicationStatus.PROCESSING.getValue().equals(application.status())) {
            log.info("Заявка #{} уже обработана, статус: {}", applicationId, application.status());
            return;
        }

        UserEntity user = userRepository.findById(application.userId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь #" + application.userId() + " не найден"));

        String inn = user.inn();

        int TIMEOUT_MS = 1500;

        CompletableFuture<ApplicationFnsSnapshotEntity> fnsFuture = CompletableFuture
                .supplyAsync(() -> fnsEnrichmentService.enrich(applicationId, inn), VT_EXECUTOR)
                .orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("Сбой или таймаут обогащения ФНС по заявке #{}: {}", applicationId, ex.getMessage());
                    return null;
                });

        CompletableFuture<ApplicationBkiSnapshotEntity> bkiFuture = CompletableFuture
                .supplyAsync(() -> bkiEnrichmentService.enrich(applicationId, inn), VT_EXECUTOR)
                .orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("Сбой или таймаут обогащения БКИ по заявке #{}: {}", applicationId, ex.getMessage());
                    return null;
                });

        CompletableFuture.allOf(fnsFuture, bkiFuture).join();

        ApplicationFnsSnapshotEntity fnsSnapshot = fnsFuture.join();
        ApplicationBkiSnapshotEntity bkiSnapshot = bkiFuture.join();

        BorrowerDossier dossier = new BorrowerDossier(user, application, fnsSnapshot, bkiSnapshot);
        DecisionResult decision = decisionEngine.evaluate(dossier);

        log.info("Решение по заявке #{}: статус = {}, причина = {}",
                applicationId, decision.status(), decision.rejectionReason());

        applicationRepository.updateDecision(
                application.id(),
                decision.status(),
                decision.rejectionReason(),
                decision.reviewReasonType(),
                decision.scoringScore(),
                decision.calculatedPdnPercent(),
                decision.maxLimitRub(),
                decision.approvedAmount(),
                decision.approvedRate()
        );
    }

    public ApplicationResponse getClientApplication(Long applicationId, Long currentUserId) {
        ApplicationEntity entity = applicationRepository.findByIdAndUserId(applicationId, currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        ApplicationResponse response = mapper.toDto(entity);
        attachScheduleIfApproved(response, entity);
        return response;
    }

    public List<ApplicationResponse> getClientApplications(Long currentUserId) {
        return applicationRepository.findByUserId(currentUserId)
                .stream()
                .map(entity -> {
                    ApplicationResponse response = mapper.toDto(entity);
                    attachScheduleIfApproved(response, entity);
                    return response;
                })
                .toList();
    }

    @Transactional
    public ApplicationResponse signApplication(Long applicationId, SignApplicationRequest request, Long currentUserId) {
        ApplicationEntity entity = applicationRepository.findByIdAndUserId(applicationId, currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        if (!ApplicationStatus.APPROVED.getValue().equals(entity.status())) {
            throw new IllegalStateException("Подписание возможно только для заявки в статусе APPROVED");
        }

        if (request.getConfirmTerms() == null || !request.getConfirmTerms()) {
            throw new IllegalArgumentException("Требуется подтверждение согласия с условиями кредитования");
        }

        if (request.getSmsCode() == null || request.getSmsCode().isBlank()) {
            throw new IllegalArgumentException("Код подтверждения не может быть пустым");
        }

        int rowsUpdated = applicationRepository.markAsSignedIfApproved(applicationId);
        if (rowsUpdated == 0) {
            throw new IllegalStateException("Заявка #" + applicationId + " уже подписана или не находится в статусе APPROVED");
        }

        ApplicationEntity updated = applicationRepository.findByIdAndUserId(applicationId, currentUserId)
                .orElseThrow();

        ApplicationResponse response = mapper.toDto(updated);
        attachScheduleIfApproved(response, updated);
        return response;
    }

    public ApplicationPageResponse getEmployeeApplicationsPaged(ApplicationStatus statusFilter, int page, int size) {
        int limit = Math.max(1, size);
        long offset = (long) Math.max(0, page) * limit;

        List<ApplicationEntity> entities;
        long totalElements;

        if (statusFilter != null) {
            String status = statusFilter.getValue();
            entities = applicationRepository.findByStatusPaged(status, limit, offset);
            totalElements = applicationRepository.countByStatus(status);
        } else {
            entities = applicationRepository.findAllPaged(limit, offset);
            totalElements = applicationRepository.count();
        }

        List<ApplicationResponse> items = entities.stream()
                .map(entity -> {
                    ApplicationResponse resp = mapper.toDto(entity);
                    attachScheduleIfApproved(resp, entity);
                    return resp;
                })
                .toList();

        int totalPages = (int) Math.ceil((double) totalElements / limit);

        ApplicationPageResponse pageResponse = new ApplicationPageResponse();
        pageResponse.setItems(items);
        pageResponse.setPage(page);
        pageResponse.setSize(limit);
        pageResponse.setTotalElements(totalElements);
        pageResponse.setTotalPages(totalPages);
        return pageResponse;
    }

    public ApplicationDetailsResponse getEmployeeApplicationDetails(Long applicationId) {
        ApplicationEntity app = applicationRepository.findWithUserById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        UserEntity user = userRepository.findById(app.userId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь #" + app.userId() + " не найден"));

        ApplicationFnsSnapshotEntity fns = fnsSnapshotRepository.findLatestByApplicationId(applicationId).orElse(null);
        ApplicationBkiSnapshotEntity bki = bkiSnapshotRepository.findLatestByApplicationId(applicationId).orElse(null);

        ApplicationDetailsResponse details = new ApplicationDetailsResponse();
        details.setId(app.id());
        details.setCompanyName(app.companyName() != null ? app.companyName() : user.companyName());
        details.setInn(app.inn() != null ? app.inn() : user.inn());
        details.setAmount(app.amount());
        details.setTermMonths(app.termMonths());
        details.setPurpose(mapper.mapPurpose(app.purpose()));
        details.setStatus(mapper.mapStatus(app.status()));
        details.setRejectionReason(app.rejectionReason());
        details.setApprovedAmount(app.approvedAmount());
        details.setApprovedRate(app.approvedRate());
        details.setCreatedAt(app.createdAt());
        details.setUpdatedAt(app.updatedAt());

        if (app.reviewReasonType() != null) {
            details.setReviewReasonCode(ReviewReasonCode.fromValue(app.reviewReasonType()));
        }
        boolean canRetry = ApplicationStatus.MANUAL_REVIEW.getValue().equals(app.status())
                && ReviewReasonCode.TECH_FAILURE.getValue().equals(app.reviewReasonType());
        details.setCanRetry(canRetry);

        UserProfile userProfile = new UserProfile();
        userProfile.setId(user.id());
        userProfile.setUsername(user.username());
        userProfile.setRole(UserRole.fromValue(user.role()));
        userProfile.setCompanyName(user.companyName());
        userProfile.setInn(user.inn());
        details.setUser(userProfile);

        if (fns != null) {
            FnsSnapshotDto fnsDto = new FnsSnapshotDto();
            fnsDto.setInn(fns.inn());
            fnsDto.setCompanyName(fns.companyName());
            fnsDto.setStateStatus(fns.stateStatus());
            fnsDto.setTaxBlocksExist(fns.taxBlocksExist());
            fnsDto.setBusinessAgeMonths(fns.businessAgeMonths());
            fnsDto.setCreatedAt(fns.createdAt());
            details.setFns(fnsDto);
        }

        if (bki != null) {
            BkiSnapshotDto bkiDto = new BkiSnapshotDto();
            bkiDto.setInn(bki.inn());
            bkiDto.setBkiScore(bki.bkiScore());
            bkiDto.setMonthlyPaymentRub(bki.monthlyPaymentRub());
            bkiDto.setCurrentOverdueDays(bki.currentOverdueDays());
            bkiDto.setCntOverdueOver90d(bki.cntOverdueOver90d());
            bkiDto.setRiskBucket(bki.riskBucket());
            bkiDto.setCreatedAt(bki.createdAt());
            details.setBki(bkiDto);
        }

        ScoringMetricsDto scoring = new ScoringMetricsDto();
        scoring.setScoringScore(app.scoringScore());
        scoring.setCalculatedPdnPercent(app.calculatedPdnPercent());
        scoring.setMaxLimitRub(app.maxLimitRub());
        details.setScoring(scoring);

        if ((ApplicationStatus.APPROVED.getValue().equals(app.status()) || ApplicationStatus.SIGNED.getValue().equals(app.status())
                || ApplicationStatus.MANUAL_REVIEW.getValue().equals(app.status()))
                && app.approvedAmount() != null && app.approvedRate() != null && app.termMonths() != null) {
            details.setSchedule(paymentScheduleService.calculateSchedule(
                    app.approvedAmount(),
                    app.termMonths(),
                    app.approvedRate(),
                    LocalDate.now(ZoneId.systemDefault())
            ));
        }
        return details;
    }

    public ApplicationResponse retryApplication(Long applicationId) {
        ApplicationEntity app = applicationRepository.findWithUserById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        if (!ApplicationStatus.MANUAL_REVIEW.getValue().equals(app.status())
                || !ReviewReasonCode.TECH_FAILURE.getValue().equals(app.reviewReasonType())) {
            throw new IllegalStateException("Перезапуск возможен только при техническом сбое внешних сервисов");
        }

        int rowsUpdated = applicationRepository.markAsProcessingIfEligibleForRetry(applicationId);
        if (rowsUpdated == 0) {
            throw new IllegalStateException("Перезапуск заявки #" + applicationId + " невозможен: заявка уже обрабатывается или не находится в статусе MANUAL_REVIEW (TECH_FAILURE)");
        }

        processApplication(applicationId);

        ApplicationEntity updated = applicationRepository.findWithUserById(applicationId).orElseThrow();
        ApplicationResponse response = mapper.toDto(updated);
        attachScheduleIfApproved(response, updated);
        return response;
    }

    @Transactional
    public ApplicationResponse manualApprove(Long applicationId, ManualApproveRequest request) {
        ApplicationEntity app = applicationRepository.findWithUserById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        if (!ApplicationStatus.MANUAL_REVIEW.getValue().equals(app.status())) {
            throw new IllegalStateException("Ручное одобрение возможно только для заявки в статусе MANUAL_REVIEW");
        }

        Long approvedAmount = request.getApprovedAmount() != null ? request.getApprovedAmount()
                : (app.approvedAmount() != null ? app.approvedAmount() : app.amount());
        Double approvedRate = request.getApprovedRate() != null ? request.getApprovedRate()
                : (app.approvedRate() != null ? app.approvedRate() : 18.5);

        int rowsUpdated = applicationRepository.markAsApprovedByEmployee(
                applicationId, approvedAmount, approvedRate, request.getComment()
        );
        return getApplicationResponse(applicationId, rowsUpdated);
    }

    @Transactional
    public ApplicationResponse manualReject(Long applicationId, ManualRejectRequest request) {
        ApplicationEntity app = applicationRepository.findWithUserById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        if (!ApplicationStatus.MANUAL_REVIEW.getValue().equals(app.status())) {
            throw new IllegalStateException("Ручное отклонение возможно только для заявки в статусе MANUAL_REVIEW");
        }

        int rowsUpdated = applicationRepository.markAsRejectedByEmployee(
                applicationId, request.getComment()
        );
        return getApplicationResponse(applicationId, rowsUpdated);
    }

    private ApplicationResponse getApplicationResponse(Long applicationId, int rowsUpdated) {
        if (rowsUpdated == 0) {
            throw new IllegalStateException("Заявка #" + applicationId + " уже обработана или не находится в статусе MANUAL_REVIEW");
        }

        ApplicationEntity updated = applicationRepository.findWithUserById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        ApplicationResponse response = mapper.toDto(updated);
        attachScheduleIfApproved(response, updated);
        return response;
    }

    private void attachScheduleIfApproved(ApplicationResponse response, ApplicationEntity entity) {
        if (ApplicationStatus.APPROVED.getValue().equals(entity.status())
                || ApplicationStatus.SIGNED.getValue().equals(entity.status())) {
            response.setSchedule(paymentScheduleService.calculateSchedule(
                    entity.approvedAmount(),
                    entity.termMonths(),
                    entity.approvedRate(),
                    LocalDate.now(ZoneId.systemDefault())
            ));
        }
    }
}
