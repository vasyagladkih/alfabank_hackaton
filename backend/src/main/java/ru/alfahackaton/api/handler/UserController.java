package ru.alfahackaton.api.handler;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.dto.DemoLoginRequest;
import ru.alfahackaton.dto.DemoLoginResponse;
import ru.alfahackaton.dto.SignApplicationRequest;
import ru.alfahackaton.service.ApplicationService;
import ru.alfahackaton.stub.authservice.service.AuthService;

import java.util.List;

/**
 * REST-контроллер операций клиента и аутентификации.
 */
@RestController
public class UserController {

    private final AuthService authService;
    private final ApplicationService applicationService;

    public UserController(AuthService authService, ApplicationService applicationService) {
        this.authService = authService;
        this.applicationService = applicationService;
    }

    @PostMapping("/api/v1/auth/demo-login")
    public ResponseEntity<DemoLoginResponse> demoLogin(@RequestBody DemoLoginRequest request) {
        DemoLoginResponse response = authService.demoLogin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/client/applications")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            @AuthenticationPrincipal Long currentUserId
    ) {
        ApplicationResponse response = applicationService.createApplication(request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/client/applications/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ApplicationResponse> getApplicationById(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Long currentUserId
    ) {
        ApplicationResponse response = applicationService.getClientApplication(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/client/applications")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<ApplicationResponse>> getClientApplications(
            @AuthenticationPrincipal Long currentUserId
    ) {
        List<ApplicationResponse> responses = applicationService.getClientApplications(currentUserId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/v1/client/applications/{id}/sign")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ApplicationResponse> signApplication(
            @PathVariable("id") Long id,
            @RequestBody SignApplicationRequest request,
            @AuthenticationPrincipal Long currentUserId
    ) {
        ApplicationResponse response = applicationService.signApplication(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }
}
