package ru.alfahackaton.api.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.dto.DemoLoginRequest;
import ru.alfahackaton.dto.DemoLoginResponse;
import ru.alfahackaton.service.ApplicationService;
import ru.alfahackaton.stub.authservice.service.AuthService;

import java.util.List;
import java.util.Objects;

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
    public ResponseEntity<ApplicationResponse> createApplication(@RequestBody CreateApplicationRequest request) {
        Long currentUserId = getCurrentUserId();
        ApplicationResponse response = applicationService.createApplication(request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/client/applications/{id}")
    public ResponseEntity<ApplicationResponse> getApplicationById(@PathVariable("id") Long id) {
        Long currentUserId = getCurrentUserId();
        String currentRole = getCurrentUserRole();
        ApplicationResponse response = applicationService.getApplicationById(id, currentUserId, currentRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/client/applications")
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
        Object principal = auth.getPrincipal();
        if (principal instanceof Long l) return l;
        if (principal instanceof Jwt jwt) {
            return Long.valueOf(Objects.requireNonNull(jwt.getSubject()));
        }
        return Long.valueOf(principal.toString());
    }

    private String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().isEmpty()) return "";
        return auth.getAuthorities().iterator().next().getAuthority();
    }
}
