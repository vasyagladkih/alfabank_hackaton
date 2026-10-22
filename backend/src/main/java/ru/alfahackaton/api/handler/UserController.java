package ru.alfahackaton.api.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.api.AuthApi;
import ru.alfahackaton.api.ClientApplicationsApi;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.dto.DemoLoginRequest;
import ru.alfahackaton.dto.DemoLoginResponse;
import ru.alfahackaton.service.ApplicationService;
import ru.alfahackaton.service.AuthService;

import java.util.List;

@RestController
public class UserController implements ClientApplicationsApi, AuthApi {

    private final AuthService authService;
    private final ApplicationService applicationService;

    public UserController(AuthService authService, ApplicationService applicationService) {
        this.authService = authService;
        this.applicationService = applicationService;
    }

    @Override
    public ResponseEntity<DemoLoginResponse> demoLogin(DemoLoginRequest request) {
        DemoLoginResponse response = authService.demoLogin(request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ApplicationResponse> createApplication(CreateApplicationRequest request) {
        Long currentUserId = getCurrentUserId();
        ApplicationResponse response = applicationService.createApplication(request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ApplicationResponse> getApplicationById(Long id) {
        Long currentUserId = getCurrentUserId();
        String currentRole = getCurrentUserRole();
        ApplicationResponse response = applicationService.getApplicationById(id, currentUserId, currentRole);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<ApplicationResponse>> getClientApplications() {
        Long currentUserId = getCurrentUserId();
        List<ApplicationResponse> responses = applicationService.getClientApplications(currentUserId);
        return ResponseEntity.ok(responses);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Пользователь не аутентифицирован");
        }
        return (Long) auth.getPrincipal();
    }

    private String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().isEmpty()) {
            return "";
        }
        return auth.getAuthorities().iterator().next().getAuthority();
    }
}
