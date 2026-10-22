package ru.alfahackaton.api.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.api.EmployeeApplicationsApi;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.service.ApplicationService;

import java.util.List;

@RestController
public class EmployeeController implements EmployeeApplicationsApi {

    private final ApplicationService applicationService;

    public EmployeeController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public ResponseEntity<List<ApplicationResponse>> getEmployeeApplications(ApplicationStatus status) {
        List<ApplicationResponse> responses = applicationService.getEmployeeApplications(status);
        return ResponseEntity.ok(responses);
    }
}