package ru.alfahackaton.api.handler;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.dto.ApplicationDetailsResponse;
import ru.alfahackaton.dto.ApplicationPageResponse;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.ManualApproveRequest;
import ru.alfahackaton.dto.ManualRejectRequest;
import ru.alfahackaton.service.ApplicationService;

/**
 * REST-контроллер операций сотрудника банка (андеррайтера).
 */
@RestController
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeController {

    private final ApplicationService applicationService;

    public EmployeeController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/api/v1/employee/applications")
    public ResponseEntity<ApplicationPageResponse> getEmployeeApplications(
            @RequestParam(value = "status", required = false) ApplicationStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        ApplicationPageResponse response = applicationService.getEmployeeApplicationsPaged(status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/employee/applications/{id}")
    public ResponseEntity<ApplicationDetailsResponse> getEmployeeApplicationDetails(
            @PathVariable("id") Long id
    ) {
        ApplicationDetailsResponse details = applicationService.getEmployeeApplicationDetails(id);
        return ResponseEntity.ok(details);
    }

    @PostMapping("/api/v1/employee/applications/{id}/retry")
    public ResponseEntity<ApplicationResponse> retryApplication(
            @PathVariable("id") Long id
    ) {
        ApplicationResponse response = applicationService.retryApplication(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/employee/applications/{id}/approve")
    public ResponseEntity<ApplicationResponse> approveApplication(
            @PathVariable("id") Long id,
            @Valid @RequestBody ManualApproveRequest request
    ) {
        ApplicationResponse response = applicationService.manualApprove(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/employee/applications/{id}/reject")
    public ResponseEntity<ApplicationResponse> rejectApplication(
            @PathVariable("id") Long id,
            @Valid @RequestBody ManualRejectRequest request
    ) {
        ApplicationResponse response = applicationService.manualReject(id, request);
        return ResponseEntity.ok(response);
    }
}
