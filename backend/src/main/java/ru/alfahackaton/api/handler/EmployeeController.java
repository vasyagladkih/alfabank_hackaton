package ru.alfahackaton.api.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.service.ApplicationService;

import java.util.List;

@RestController
public class EmployeeController {

    private final ApplicationService applicationService;

    public EmployeeController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/api/v1/employee/applications")
    public ResponseEntity<List<ApplicationResponse>> getEmployeeApplications(
            @RequestParam(value = "status", required = false) ApplicationStatus status
    ) {
        List<ApplicationResponse> responses = applicationService.getEmployeeApplications(status);
        return ResponseEntity.ok(responses);
    }
}
