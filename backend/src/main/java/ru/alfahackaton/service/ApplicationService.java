package ru.alfahackaton.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.mapper.ApplicationMapper;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.UserEntity;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationMapper mapper;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            UserRepository userRepository,
            TransactionTemplate transactionTemplate, ApplicationMapper mapper
    ) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = transactionTemplate;
        this.mapper = mapper;
    }

    @Transactional
    public ApplicationResponse createApplication(CreateApplicationRequest request, Long currentUserId) {

        UserEntity user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());

        ApplicationEntity entity = new ApplicationEntity(
                null,
                currentUserId,
                request.getAmount(),
                request.getTermMonths(),
                request.getPurpose().getValue(),
                ApplicationStatus.PROCESSING.getValue(),
                null,
                now,
                now,
                user.companyName(),
                user.inn()
        );

        ApplicationEntity saved = applicationRepository.save(entity);

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3000);
                String finalStatus;
                String rejectionReason = null;

                // Бизнес-логика скоринга: отказ для тестового ИНН плохой компании или суммы > 30 млн
                if ("7702999999".equals(user.inn()) || request.getAmount() > 30_000_000L) {
                    finalStatus = ApplicationStatus.REJECTED.getValue();
                    rejectionReason = "Превышен показатель предельной долговой нагрузки (ПДН > 80%)";
                } else {
                    finalStatus = ApplicationStatus.APPROVED.getValue();
                }

                String effectiveRejectionReason = rejectionReason;
                transactionTemplate.executeWithoutResult(status ->
                        applicationRepository.updateStatus(saved.id(), finalStatus, effectiveRejectionReason)
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ApplicationResponse response = mapper.toDto(saved);
        if (response.getCompanyName() == null) response.setCompanyName(user.companyName());
        if (response.getInn() == null) response.setInn(user.inn());
        return response;
    }

    public ApplicationResponse getApplicationById(Long applicationId, Long currentUserId, String currentRole) {
        ApplicationEntity entity = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка #" + applicationId + " не найдена"));

        if (("ROLE_CLIENT".equals(currentRole) || "CLIENT".equals(currentRole)) && !entity.userId().equals(currentUserId)) {
            throw new AccessDeniedException("Доступ к чужой заявке запрещен");
        }

        return mapper.toDto(entity);
    }

    public List<ApplicationResponse> getClientApplications(Long currentUserId) {
        return applicationRepository.findByUserId(currentUserId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    public List<ApplicationResponse> getEmployeeApplications(ApplicationStatus status) {
        String filter = status != null ? status.getValue() : null;
        return applicationRepository.findAll(filter)
                .stream()
                .map(mapper::toDto)
                .toList();
    }
}
