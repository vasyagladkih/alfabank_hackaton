package ru.alfahackaton.api.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.api.EmployeeApplicationsApi;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;

import java.util.List;


@RestController
public class EmplController implements EmployeeApplicationsApi {

    @Override
    public ResponseEntity<List<ApplicationResponse>> getEmployeeApplications(ApplicationStatus status) {...}
}