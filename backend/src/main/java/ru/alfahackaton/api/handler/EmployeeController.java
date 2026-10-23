package ru.alfahackaton.api.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.api.EmployeeApplicationsApi;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.EmployeeDecisionRequest;
import ru.alfahackaton.dto.EmployeeDossier;
import ru.alfahackaton.service.ReviewWorkflowService;

import java.util.List;

@RestController
public class EmployeeController implements EmployeeApplicationsApi {

    private final ReviewWorkflowService review;

    public EmployeeController(ReviewWorkflowService review) {
        this.review = review;
    }

    @Override
    public ResponseEntity<List<ApplicationResponse>> getEmployeeApplications(ApplicationStatus status, String search) {
        return ResponseEntity.ok(review.listApplications(status, search, currentUserId()));
    }

    @Override
    public ResponseEntity<EmployeeDossier> getEmployeeDossier(Long id) {
        return ResponseEntity.ok(review.getDossier(id, currentUserId()));
    }

    @Override
    public ResponseEntity<EmployeeDossier> decideApplication(Long id, EmployeeDecisionRequest request) {
        return ResponseEntity.ok(review.decide(id, request, currentUserId()));
    }

    private long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new AccessDeniedException("Требуется авторизация");
        }
        return id;
    }
}
