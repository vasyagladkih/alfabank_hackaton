package ru.alfahackaton.api.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.CreateApplicationRequest;
import ru.alfahackaton.dto.DemoLoginRequest;
import ru.alfahackaton.dto.DemoLoginResponse;
import ru.alfahackaton.dto.LoanPurpose;
import ru.alfahackaton.dto.SignApplicationRequest;
import ru.alfahackaton.dto.UserCode;
import ru.alfahackaton.dto.UserProfile;
import ru.alfahackaton.dto.UserRole;
import ru.alfahackaton.persistance.repository.ApplicationBkiSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationFnsSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.service.ApplicationService;
import ru.alfahackaton.stub.authservice.service.AuthService;
import ru.alfahackaton.stub.authservice.service.StubTokenService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UserControllerIntegrationTest.TestConfig.class)
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private StubTokenService stubTokenService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ApplicationService applicationService;

    @MockitoBean
    private ApplicationRepository applicationRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ApplicationFnsSnapshotRepository fnsSnapshotRepository;

    @MockitoBean
    private ApplicationBkiSnapshotRepository bkiSnapshotRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public DataSource dataSource() throws SQLException {
            DataSource ds = Mockito.mock(DataSource.class);
            Connection conn = Mockito.mock(Connection.class);
            DatabaseMetaData meta = Mockito.mock(DatabaseMetaData.class);
            Mockito.when(ds.getConnection()).thenReturn(conn);
            Mockito.when(conn.getMetaData()).thenReturn(meta);
            Mockito.when(meta.getDatabaseProductName()).thenReturn("PostgreSQL");
            return ds;
        }
    }

    @Test
    @DisplayName("POST /api/v1/auth/demo-login: успешная аутентификация заемщика")
    void testDemoLoginSuccess() throws Exception {
        DemoLoginRequest request = new DemoLoginRequest();
        request.setUserCode(UserCode.CLIENT_GOOD);

        DemoLoginResponse response = new DemoLoginResponse();
        response.setToken("mock-jwt-token-xyz");
        UserProfile profile = new UserProfile();
        profile.setId(101L);
        profile.setUsername("client_good");
        profile.setRole(UserRole.CLIENT);
        response.setUser(profile);

        Mockito.when(authService.demoLogin(any(DemoLoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token-xyz"))
                .andExpect(jsonPath("$.user.username").value("client_good"))
                .andExpect(jsonPath("$.user.role").value("CLIENT"));
    }

    @Test
    @DisplayName("POST /api/v1/client/applications: создание заявки клиентом возвращает 201 CREATED")
    void testCreateApplicationSuccess() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_user", "CLIENT", "7701234567");

        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setAmount(1_500_000L);
        request.setTermMonths(12);
        request.setPurpose(LoanPurpose.WORKING_CAPITAL);

        ApplicationResponse appResponse = new ApplicationResponse();
        appResponse.setId(10L);
        appResponse.setAmount(1_500_000L);
        appResponse.setTermMonths(12);
        appResponse.setStatus(ApplicationStatus.PROCESSING);

        Mockito.when(applicationService.createApplication(any(CreateApplicationRequest.class), eq(101L)))
                .thenReturn(appResponse);

        mockMvc.perform(post("/api/v1/client/applications")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.amount").value(1500000))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("POST /api/v1/client/applications: запрос без JWT токена отклоняется со статусом 401")
    void testCreateApplicationWithoutTokenUnauthorized() throws Exception {
        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setAmount(1_500_000L);
        request.setTermMonths(12);
        request.setPurpose(LoanPurpose.WORKING_CAPITAL);

        mockMvc.perform(post("/api/v1/client/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/client/applications: сотрудник банка не имеет доступа к созданию клиентской заявки (403)")
    void testCreateApplicationForbiddenForEmployee() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        CreateApplicationRequest request = new CreateApplicationRequest();
        request.setAmount(1_500_000L);
        request.setTermMonths(12);
        request.setPurpose(LoanPurpose.WORKING_CAPITAL);

        mockMvc.perform(post("/api/v1/client/applications")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/client/applications/{id}: клиент успешно получает заявку по ID")
    void testGetApplicationByIdSuccess() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_user", "CLIENT", "7701234567");

        ApplicationResponse appResponse = new ApplicationResponse();
        appResponse.setId(10L);
        appResponse.setStatus(ApplicationStatus.APPROVED);
        appResponse.setApprovedAmount(1_500_000L);
        appResponse.setApprovedRate(18.8);

        Mockito.when(applicationService.getClientApplication(10L, 101L)).thenReturn(appResponse);

        mockMvc.perform(get("/api/v1/client/applications/10")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(1500000))
                .andExpect(jsonPath("$.approvedRate").value(18.8));
    }

    @Test
    @DisplayName("GET /api/v1/client/applications: клиент успешно получает список всех своих заявок")
    void testGetClientApplicationsListSuccess() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_user", "CLIENT", "7701234567");

        ApplicationResponse appResponse = new ApplicationResponse();
        appResponse.setId(10L);
        appResponse.setStatus(ApplicationStatus.PROCESSING);

        Mockito.when(applicationService.getClientApplications(101L)).thenReturn(List.of(appResponse));

        mockMvc.perform(get("/api/v1/client/applications")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].status").value("PROCESSING"));
    }

    @Test
    @DisplayName("POST /api/v1/client/applications/{id}/sign: клиент успешно подписывает одобренную заявку")
    void testSignApplicationSuccess() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_user", "CLIENT", "7701234567");

        SignApplicationRequest request = new SignApplicationRequest();
        request.setConfirmTerms(true);
        request.setSmsCode("1234");

        ApplicationResponse appResponse = new ApplicationResponse();
        appResponse.setId(10L);
        appResponse.setStatus(ApplicationStatus.SIGNED);

        Mockito.when(applicationService.signApplication(eq(10L), any(SignApplicationRequest.class), eq(101L)))
                .thenReturn(appResponse);

        mockMvc.perform(post("/api/v1/client/applications/10/sign")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("SIGNED"));
    }

    @Test
    @DisplayName("POST /api/v1/client/applications/{id}/sign: ошибка валидации возвращает 400 Bad Request")
    void testSignApplicationValidationError() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_user", "CLIENT", "7701234567");

        SignApplicationRequest request = new SignApplicationRequest();
        request.setConfirmTerms(false);
        request.setSmsCode("");

        Mockito.when(applicationService.signApplication(eq(10L), any(SignApplicationRequest.class), eq(101L)))
                .thenThrow(new IllegalArgumentException("Код подтверждения не может быть пустым"));

        mockMvc.perform(post("/api/v1/client/applications/10/sign")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
