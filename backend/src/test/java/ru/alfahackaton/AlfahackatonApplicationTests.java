package ru.alfahackaton;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.alfahackaton.dto.ApplicationStatus;
import ru.alfahackaton.dto.ReviewReasonCode;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.UserEntity;
import ru.alfahackaton.persistance.repository.ApplicationBkiSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationFnsSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.stub.authservice.service.StubTokenService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.sql.init.mode=always"
})
class AlfahackatonApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTokenService stubTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationFnsSnapshotRepository fnsSnapshotRepository;

    @Autowired
    private ApplicationBkiSnapshotRepository bkiSnapshotRepository;

    @Test
    @DisplayName("Контекст Spring Boot успешно поднимается с реальным PostgreSQL в Testcontainers")
    void contextLoads() throws Exception {
        assertNotNull(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
    }

    @Test
    @DisplayName("PostgreSQL Testcontainers: сохранение, чтение с JOIN (findWithUserById) и целостность схемы")
    void testRealDatabasePersistenceAndCustomQueries() {
        UserEntity user = new UserEntity(
                null, "tc_user_" + System.currentTimeMillis(), "CLIENT", "ООО \"ТестКонтейнер\"", "7701999888",
                1_000_000L, 12_000_000L, 500_000L, 24, OffsetDateTime.now()
        );
        UserEntity savedUser = userRepository.save(user);
        assertNotNull(savedUser.id());

        ApplicationEntity app = new ApplicationEntity(
                null, savedUser.id(), 1_500_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null, OffsetDateTime.now(), OffsetDateTime.now(),
                null, null
        );
        ApplicationEntity savedApp = applicationRepository.save(app);
        assertNotNull(savedApp.id());

        Optional<ApplicationEntity> found = applicationRepository.findWithUserById(savedApp.id());
        assertTrue(found.isPresent());
        assertEquals(savedUser.id(), found.get().userId());
        assertEquals(1_500_000L, found.get().amount());
        assertEquals(12, found.get().termMonths());
        assertEquals("WORKING_CAPITAL", found.get().purpose());
        assertEquals("ООО \"ТестКонтейнер\"", found.get().companyName());
        assertEquals("7701999888", found.get().inn());
        assertEquals(ApplicationStatus.PROCESSING.getValue(), found.get().status());
    }

    @Test
    @DisplayName("PostgreSQL Testcontainers: физическая проверка атомарного CAS markAsSignedIfApproved")
    void testRealPostgresAtomicCasSignApplication() {
        UserEntity user = new UserEntity(
                null, "sign_user_" + System.currentTimeMillis(), "CLIENT", "ООО \"Подпись\"", "7701112233",
                2_000_000L, 24_000_000L, 800_000L, 36, OffsetDateTime.now()
        );
        UserEntity savedUser = userRepository.save(user);

        ApplicationEntity app = new ApplicationEntity(
                null, savedUser.id(), 2_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null, OffsetDateTime.now(), OffsetDateTime.now(),
                null, null
        );
        ApplicationEntity savedApp = applicationRepository.save(app);

        // 1. Первый вызов на строке в статусе APPROVED должен обновить 1 запись
        int firstUpdate = applicationRepository.markAsSignedIfApproved(savedApp.id());
        assertEquals(1, firstUpdate, "Первый вызов должен обновить ровно 1 строку");

        // 2. Второй вызов на той же строке (уже SIGNED) должен обновить 0 записей
        int secondUpdate = applicationRepository.markAsSignedIfApproved(savedApp.id());
        assertEquals(0, secondUpdate, "Второй вызов должен вернуть 0 измененных строк");

        // Проверяем актуальное состояние в БД
        ApplicationEntity updated = applicationRepository.findWithUserById(savedApp.id()).orElseThrow();
        assertEquals(ApplicationStatus.SIGNED.getValue(), updated.status());
    }

    @Test
    @DisplayName("PostgreSQL Testcontainers: физическая проверка атомарного CAS markAsProcessingIfEligibleForRetry")
    void testRealPostgresAtomicCasRetryApplication() {
        UserEntity user = new UserEntity(
                null, "retry_user_" + System.currentTimeMillis(), "CLIENT", "ООО \"Рестарт\"", "7701334455",
                2_000_000L, 24_000_000L, 800_000L, 36, OffsetDateTime.now()
        );
        UserEntity savedUser = userRepository.save(user);

        ApplicationEntity app = new ApplicationEntity(
                null, savedUser.id(), 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.MANUAL_REVIEW.getValue(), "Сбой ФНС", ReviewReasonCode.TECH_FAILURE.getValue(),
                null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(),
                null, null
        );
        ApplicationEntity savedApp = applicationRepository.save(app);

        // 1. Первый вызов на строке MANUAL_REVIEW / TECH_FAILURE обновляет 1 запись
        int firstUpdate = applicationRepository.markAsProcessingIfEligibleForRetry(savedApp.id());
        assertEquals(1, firstUpdate, "Первый вызов retry должен перевести заявку в PROCESSING");

        // 2. Второй вызов обновляет 0 записей
        int secondUpdate = applicationRepository.markAsProcessingIfEligibleForRetry(savedApp.id());
        assertEquals(0, secondUpdate, "Второй вызов retry должен вернуть 0 измененных строк");

        ApplicationEntity updated = applicationRepository.findWithUserById(savedApp.id()).orElseThrow();
        assertEquals(ApplicationStatus.PROCESSING.getValue(), updated.status());
        assertEquals(null, updated.rejectionReason());
        assertEquals(null, updated.reviewReasonType());
    }

    @Test
    @DisplayName("Эмулятор Alfa-ID генерирует валидный RS256 токен, который расшифровывается публичным ключом")
    void testRsaTokenGenerationAndPublicVerification() {
        String token = stubTokenService.generateToken(101L, "client_good", "CLIENT", "7701234567");
        assertNotNull(token);

        Jwt jwt = jwtDecoder.decode(token);
        assertEquals("101", jwt.getSubject());
        assertEquals("client_good", jwt.getClaimAsString("username"));
        assertEquals("CLIENT", jwt.getClaimAsString("role"));
        assertEquals("7701234567", jwt.getClaimAsString("inn"));
        assertEquals("https://id.stubAuthService.ru", Objects.requireNonNull(jwt.getIssuer()).toString());
    }

    @Test
    @DisplayName("Запрос к защищенному эндпоинту без токена возвращает 401 Unauthorized")
    void testProtectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/client/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Запрос клиента с валидным токеном RS256 успешно проходит авторизацию")
    void testClientEndpointWithValidRsaToken() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_good", "CLIENT", "7701234567");

        mockMvc.perform(get("/api/v1/client/applications")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Попытка клиента с ролью CLIENT зайти в кабинет сотрудника возвращает 403 Forbidden")
    void testClientRoleCannotAccessEmployeeEndpoints() throws Exception {
        String clientToken = stubTokenService.generateToken(101L, "client_good", "CLIENT", "7701234567");

        mockMvc.perform(get("/api/v1/employee/applications")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }
}
