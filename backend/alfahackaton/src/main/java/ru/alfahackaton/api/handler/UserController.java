package ru.alfahackaton.api.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.api.AuthApi;
import ru.alfahackaton.api.ClientApplicationsApi;
import ru.alfahackaton.dto.*;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class UserController implements ClientApplicationsApi, AuthApi {

    @Override
    public ResponseEntity<DemoLoginResponse> demoLogin(DemoLoginRequest request) {...}

    @Override
    public ResponseEntity<ApplicationResponse> createApplication(CreateApplicationRequest request) {...}

    @Override
    public ResponseEntity<ApplicationResponse> getApplicationById(Long id) {...}

    @Override
    public ResponseEntity<List<ApplicationResponse>> getClientApplications() {...}
}
