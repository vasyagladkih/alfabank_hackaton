package ru.alfahackaton.api.handler;

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
import ru.alfahackaton.dto.ApplicationDetailsResponse;
import ru.alfahackaton.dto.ApplicationPageResponse;
import ru.alfahackaton.dto.ApplicationResponse;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.ManualApproveRequest;
import ru.alfahackaton.dto.ManualRejectRequest;
import ru.alfahackaton.dto.ReviewReasonCode;
import ru.alfahackaton.persistance.repository.ApplicationBkiSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationFnsSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.service.ApplicationService;
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
@Import(EmployeeControllerIntegrationTest.TestConfig.class)
class EmployeeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTokenService stubTokenService;

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
    @DisplayName("Сотрудник банка успешно получает пагинированный реестр заявок")
    void testGetEmployeeApplicationsPagedSuccess() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        ApplicationPageResponse pageResponse = new ApplicationPageResponse();
        pageResponse.setPage(0);
        pageResponse.setSize(10);
        pageResponse.setTotalElements(1L);
        pageResponse.setTotalPages(1);

        ApplicationResponse app = new ApplicationResponse();
        app.setId(42L);
        app.setStatus(ApplicationStatus.PROCESSING);
        pageResponse.setItems(List.of(app));

        Mockito.when(applicationService.getEmployeeApplicationsPaged(null, 0, 10)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/employee/applications?page=0&size=10")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(42));

        Mockito.verify(applicationService).getEmployeeApplicationsPaged(null, 0, 10);
    }

    @Test
    @DisplayName("Сотрудник банка успешно получает полное досье заявки")
    void testGetEmployeeApplicationDetailsSuccess() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        ApplicationDetailsResponse details = new ApplicationDetailsResponse();
        details.setId(42L);
        details.setCompanyName("ООО Ромашка");
        details.setInn("7701234567");
        details.setStatus(ApplicationStatus.MANUAL_REVIEW);
        details.setReviewReasonCode(ReviewReasonCode.TECH_FAILURE);
        details.setCanRetry(true);

        Mockito.when(applicationService.getEmployeeApplicationDetails(42L)).thenReturn(details);

        mockMvc.perform(get("/api/v1/employee/applications/42")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.companyName").value("ООО Ромашка"))
                .andExpect(jsonPath("$.inn").value("7701234567"))
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.reviewReasonCode").value("TECH_FAILURE"))
                .andExpect(jsonPath("$.canRetry").value(true));

        Mockito.verify(applicationService).getEmployeeApplicationDetails(42L);
    }

    @Test
    @DisplayName("Сотрудник банка успешно перезапускает конвейер при TECH_FAILURE")
    void testRetryApplicationSuccess() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        ApplicationResponse retryResponse = new ApplicationResponse();
        retryResponse.setId(42L);
        retryResponse.setStatus(ApplicationStatus.PROCESSING);

        Mockito.when(applicationService.retryApplication(42L)).thenReturn(retryResponse);

        mockMvc.perform(post("/api/v1/employee/applications/42/retry")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        Mockito.verify(applicationService).retryApplication(42L);
    }

    @Test
    @DisplayName("Запрос сотрудника без JWT токена отклоняется со статусом 401 Unauthorized")
    void testUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/employee/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Запрос к эндпоинтам сотрудника с ролью CLIENT возвращает 403 Forbidden")
    void testForbiddenForClientRole() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_user", "CLIENT", "7701234567");

        mockMvc.perform(get("/api/v1/employee/applications")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/employee/applications/42")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/employee/applications/42/retry")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Сотрудник передает фильтр по статусу ?status=MANUAL_REVIEW")
    void testGetEmployeeApplicationsWithStatusFilter() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        ApplicationPageResponse pageResponse = new ApplicationPageResponse();
        pageResponse.setPage(0);
        pageResponse.setSize(10);
        pageResponse.setTotalElements(0L);
        pageResponse.setTotalPages(0);
        pageResponse.setItems(List.of());

        Mockito.when(applicationService.getEmployeeApplicationsPaged(ApplicationStatus.MANUAL_REVIEW, 0, 10))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/employee/applications?status=MANUAL_REVIEW&page=0&size=10")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        Mockito.verify(applicationService).getEmployeeApplicationsPaged(ApplicationStatus.MANUAL_REVIEW, 0, 10);
    }

    @Test
    @DisplayName("Попытка перезапуска при несуществующей заявке возвращает 400 Bad Request")
    void testRetryApplicationNotFound() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        Mockito.when(applicationService.retryApplication(999L))
                .thenThrow(new IllegalArgumentException("Заявка #999 не найдена"));

        mockMvc.perform(post("/api/v1/employee/applications/999/retry")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Заявка #999 не найдена"));
    }

    @Test
    @DisplayName("Сотрудник банка успешно одобряет заявку вручную через POST /approve")
    void testManualApproveSuccess() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        ApplicationResponse approveResponse = new ApplicationResponse();
        approveResponse.setId(42L);
        approveResponse.setStatus(ApplicationStatus.APPROVED);
        approveResponse.setApprovedAmount(1_200_000L);
        approveResponse.setApprovedRate(17.5);

        Mockito.when(applicationService.manualApprove(eq(42L), any(ManualApproveRequest.class)))
                .thenReturn(approveResponse);

        String jsonRequest = """
                {
                    "approvedAmount": 1200000,
                    "approvedRate": 17.5,
                    "comment": "Одобрено андеррайтером"
                }
                """;

        mockMvc.perform(post("/api/v1/employee/applications/42/approve")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(1200000))
                .andExpect(jsonPath("$.approvedRate").value(17.5));

        Mockito.verify(applicationService).manualApprove(eq(42L), any(ManualApproveRequest.class));
    }

    @Test
    @DisplayName("Сотрудник банка успешно отклоняет заявку вручную через POST /reject")
    void testManualRejectSuccess() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        ApplicationResponse rejectResponse = new ApplicationResponse();
        rejectResponse.setId(42L);
        rejectResponse.setStatus(ApplicationStatus.REJECTED);
        rejectResponse.setRejectionReason("Отказ по результатам проверки");

        Mockito.when(applicationService.manualReject(eq(42L), any(ManualRejectRequest.class)))
                .thenReturn(rejectResponse);

        String jsonRequest = """
                {
                    "comment": "Отказ по результатам проверки"
                }
                """;

        mockMvc.perform(post("/api/v1/employee/applications/42/reject")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Отказ по результатам проверки"));

        Mockito.verify(applicationService).manualReject(eq(42L), any(ManualRejectRequest.class));
    }

    @Test
    @DisplayName("Клиент с ролью CLIENT не может вызвать /approve или /reject (403 Forbidden)")
    void testApproveAndRejectForbiddenForClient() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_user", "CLIENT", "7701234567");

        String approveJson = """
                {
                    "approvedAmount": 1000000,
                    "approvedRate": 18.0,
                    "comment": "Попытка одобрения клиентом"
                }
                """;
        mockMvc.perform(post("/api/v1/employee/applications/42/approve")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson))
                .andExpect(status().isForbidden());

        String rejectJson = """
                {
                    "comment": "Попытка отклонения клиентом"
                }
                """;
        mockMvc.perform(post("/api/v1/employee/applications/42/reject")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Вызовы /approve и /reject без токена возвращают 401 Unauthorized")
    void testApproveAndRejectUnauthorizedWithoutToken() throws Exception {
        String approveJson = """
                {
                    "approvedAmount": 1000000,
                    "approvedRate": 18.0,
                    "comment": "Без токена"
                }
                """;
        mockMvc.perform(post("/api/v1/employee/applications/42/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson))
                .andExpect(status().isUnauthorized());

        String rejectJson = """
                {
                    "comment": "Без токена"
                }
                """;
        mockMvc.perform(post("/api/v1/employee/applications/42/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Ручное одобрение: при невалидном статусе заявки (IllegalStateException) возвращается 400 Bad Request")
    void testManualApproveConflictStatusReturnsBadRequest() throws Exception {
        String employeeToken = stubTokenService.generateToken(201L, "bank_employee", "EMPLOYEE", "7728168971");

        Mockito.when(applicationService.manualApprove(eq(42L), any(ManualApproveRequest.class)))
                .thenThrow(new IllegalStateException("Ручное одобрение возможно только для заявки в статусе MANUAL_REVIEW"));

        String jsonRequest = """
                {
                    "comment": "Попытка одобрить заявку в статусе PROCESSING"
                }
                """;

        mockMvc.perform(post("/api/v1/employee/applications/42/approve")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Ручное одобрение возможно только для заявки в статусе MANUAL_REVIEW"));
    }
}
