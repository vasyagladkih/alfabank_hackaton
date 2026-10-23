package ru.alfahackaton.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.EvidenceRepository;
import ru.alfahackaton.persistance.repository.ProcessingJobRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.service.exception.ApplicationNotFoundException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ApplicationService {
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final SnapshotService snapshots;
    private final ProcessingJobRepository jobs;
    private final EvidenceRepository evidence;
    private final ApplicationMapper mapper;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applications, UserRepository users, SnapshotService snapshots,
                              ProcessingJobRepository jobs, EvidenceRepository evidence, ApplicationMapper mapper,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.applications = applications;
        this.users = users;
        this.snapshots = snapshots;
        this.jobs = jobs;
        this.evidence = evidence;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.clock = clock;
    }

    public ApplicationResponse createApplication(CreateApplicationRequest request, Long currentUserId) {
        if (request == null || request.getAmount() == null || request.getAmount() < 100000 || request.getAmount() > 50000000
                || request.getTermMonths() == null || request.getTermMonths() < 1 || request.getTermMonths() > 60 || request.getPurpose() == null) {
            throw new IllegalArgumentException("Сумма, срок и цель должны соответствовать демонстрационному продукту");
        }
        return transactions.execute(tx -> {
            var user = users.findById(currentUserId).orElseThrow(() -> new AccessDeniedException("Демо-пользователь недоступен"));
            if (!"CLIENT".equals(user.role())) throw new AccessDeniedException("Требуется роль клиента");
            OffsetDateTime now = OffsetDateTime.now(clock);
            var saved = applications.save(new ApplicationEntity(null, currentUserId, request.getAmount(), request.getTermMonths(),
                    request.getPurpose().getValue(), "PROCESSING", null, now, now, user.companyName(), user.inn()));
            snapshots.capture(saved, "DEMO_PROFILE");
            evidence.appendAudit(saved.id(), "CREATED", "APPLICATION_CREATED", "CLIENT", currentUserId,
                    null, "PROCESSING", 0, "Учебная заявка создана; зафиксированы входы mock-провайдеров.", "{\"demoOnly\":true}", now);
            jobs.enqueue(saved.id(), now);
            return mapper.toDto(saved);
        });
    }

    public ApplicationResponse getApplicationById(Long applicationId, Long currentUserId, String currentRole) {
        var application = applications.findById(applicationId).orElseThrow(ApplicationNotFoundException::new);
        if (!("CLIENT".equals(currentRole) || "ROLE_CLIENT".equals(currentRole)) || !application.userId().equals(currentUserId)) {
            throw new AccessDeniedException("Доступ к чужой заявке запрещён");
        }
        return mapper.toDto(application);
    }

    public List<ApplicationResponse> getClientApplications(Long currentUserId) {
        return applications.findByUserId(currentUserId).stream().map(mapper::toDto).toList();
    }

    public List<ApplicationResponse> getEmployeeApplications(ApplicationStatus status) {
        return applications.findAll(status == null ? null : status.getValue()).stream().map(mapper::toDto).toList();
    }
}
