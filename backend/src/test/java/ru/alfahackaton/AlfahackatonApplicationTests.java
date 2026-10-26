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
import ru.alfahackaton.persistance.entity.ApplicationBkiSnapshotEntity;
import ru.alfahackaton.persistance.entity.ApplicationEntity;
import ru.alfahackaton.persistance.entity.UserEntity;
import ru.alfahackaton.persistance.repository.ApplicationBkiSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationFnsSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.stub.authservice.service.StubTokenService;
import ru.alfahackaton.stub.bki.entity.MockBkiEntity;
import ru.alfahackaton.stub.bki.repository.MockBkiRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
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

    @Autowired
    private MockBkiRepository mockBkiRepository;

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

    @Test
    @DisplayName("PostgreSQL Testcontainers: физическая проверка атомарного CAS markAsApprovedByEmployee")
    void testRealPostgresAtomicCasApproveApplication() {
        UserEntity user = new UserEntity(
                null, "approve_user_" + System.currentTimeMillis(), "CLIENT", "ООО \"Одобрение\"", "7701445566",
                2_000_000L, 24_000_000L, 800_000L, 36, OffsetDateTime.now()
        );
        UserEntity savedUser = userRepository.save(user);

        ApplicationEntity app = new ApplicationEntity(
                null, savedUser.id(), 1_500_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.MANUAL_REVIEW.getValue(), "Требуется проверка",
                ReviewReasonCode.BORDERLINE_SCORE.getValue(),
                null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(),
                null, null
        );
        ApplicationEntity savedApp = applicationRepository.save(app);

        // 1. Первый вызов CAS переводит в APPROVED
        int firstUpdate = applicationRepository.markAsApprovedByEmployee(savedApp.id(), 1_300_000L, 17.5, "Одобрено андеррайтером");
        assertEquals(1, firstUpdate, "Первый вызов CAS approve должен обновить ровно 1 запись");

        // 2. Второй вызов возвращает 0 (заявка уже APPROVED)
        int secondUpdate = applicationRepository.markAsApprovedByEmployee(savedApp.id(), 1_300_000L, 17.5, "Повтор");
        assertEquals(0, secondUpdate, "Второй вызов CAS approve должен вернуть 0 записей");

        // Проверяем актуальное состояние в БД
        ApplicationEntity updated = applicationRepository.findWithUserById(savedApp.id()).orElseThrow();
        assertEquals(ApplicationStatus.APPROVED.getValue(), updated.status());
        assertEquals(1_300_000L, updated.approvedAmount());
        assertEquals(17.5, updated.approvedRate());
        assertEquals("Одобрено андеррайтером", updated.rejectionReason());
    }

    @Test
    @DisplayName("PostgreSQL Testcontainers: физическая проверка атомарного CAS markAsRejectedByEmployee")
    void testRealPostgresAtomicCasRejectApplication() {
        UserEntity user = new UserEntity(
                null, "reject_user_" + System.currentTimeMillis(), "CLIENT", "ООО \"Отказ\"", "7701556677",
                2_000_000L, 24_000_000L, 800_000L, 36, OffsetDateTime.now()
        );
        UserEntity savedUser = userRepository.save(user);

        ApplicationEntity app = new ApplicationEntity(
                null, savedUser.id(), 1_500_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.MANUAL_REVIEW.getValue(), "Требуется проверка",
                ReviewReasonCode.BORDERLINE_SCORE.getValue(),
                null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(),
                null, null
        );
        ApplicationEntity savedApp = applicationRepository.save(app);

        // 1. Первый вызов CAS переводит в REJECTED
        int firstUpdate = applicationRepository.markAsRejectedByEmployee(savedApp.id(), "Отказ: стоп-факторы");
        assertEquals(1, firstUpdate, "Первый вызов CAS reject должен обновить ровно 1 запись");

        // 2. Второй вызов возвращает 0 (заявка уже REJECTED)
        int secondUpdate = applicationRepository.markAsRejectedByEmployee(savedApp.id(), "Повтор");
        assertEquals(0, secondUpdate, "Второй вызов CAS reject должен вернуть 0 записей");

        // Проверяем актуальное состояние в БД
        ApplicationEntity updated = applicationRepository.findWithUserById(savedApp.id()).orElseThrow();
        assertEquals(ApplicationStatus.REJECTED.getValue(), updated.status());
        assertEquals("Отказ: стоп-факторы", updated.rejectionReason());
    }

    @Test
    @DisplayName("PostgreSQL Testcontainers: проверка findProcessingApplicationIds возвращает только заявки PROCESSING в хронологическом порядке")
    void testRealPostgresFindProcessingApplicationIds() {
        UserEntity user = new UserEntity(
                null, "proc_user_" + System.currentTimeMillis(), "CLIENT", "ООО \"Очередь\"", "7701667788",
                1_000_000L, 12_000_000L, 500_000L, 24, OffsetDateTime.now()
        );
        UserEntity savedUser = userRepository.save(user);

        ApplicationEntity app1 = new ApplicationEntity(
                null, savedUser.id(), 500_000L, 6, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null, OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusMinutes(5),
                null, null
        );
        ApplicationEntity app2 = new ApplicationEntity(
                null, savedUser.id(), 700_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null, OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().minusMinutes(1),
                null, null
        );
        ApplicationEntity app3 = new ApplicationEntity(
                null, savedUser.id(), 900_000L, 24, "WORKING_CAPITAL",
                ApplicationStatus.APPROVED.getValue(), null, OffsetDateTime.now(), OffsetDateTime.now(),
                null, null
        );

        ApplicationEntity saved1 = applicationRepository.save(app1);
        ApplicationEntity saved2 = applicationRepository.save(app2);
        applicationRepository.save(app3);

        List<Long> processingIds = applicationRepository.findProcessingApplicationIds();
        assertNotNull(processingIds);
        assertTrue(processingIds.contains(saved1.id()));
        assertTrue(processingIds.contains(saved2.id()));
        // Заявка в статусе APPROVED не должна попадать в список
        assertTrue(processingIds.indexOf(saved1.id()) < processingIds.indexOf(saved2.id()), "Заявки должны быть упорядочены по created_at ASC");
    }

    @Test
    @DisplayName("PostgreSQL Testcontainers: проверка маппинга cnt_overdue_over_90d в MockBkiEntity и ApplicationBkiSnapshotEntity")
    void testRealPostgresBkiEntityMappingWithColumnCntOverdueOver90d() {
        // 1. Проверяем чтение предзаполненных данных из mock_bki_registry (из data.sql)
        MockBkiEntity existingBki = mockBkiRepository.findById("7702999999").orElseThrow();
        assertEquals("7702999999", existingBki.inn());
        assertEquals(320, existingBki.bkiScore());
        assertEquals(300_000L, existingBki.totalMonthlyPaymentRub());
        assertEquals(45, existingBki.currentOverdueDays());
        assertEquals(2, existingBki.cntOverdueOver90d(), "Колонка cnt_overdue_over_90d должна корректно читаться из mock_bki_registry");

        // 2. Проверяем сохранение и чтение в ApplicationBkiSnapshotEntity
        String testInn = "7701234567";
        UserEntity user = new UserEntity(
                null, "bki_user_" + System.currentTimeMillis(), "CLIENT", "ООО \"БКИ Тест\"", testInn,
                1_000_000L, 12_000_000L, 500_000L, 24, OffsetDateTime.now()
        );
        UserEntity savedUser = userRepository.save(user);

        ApplicationEntity app = new ApplicationEntity(
                null, savedUser.id(), 1_000_000L, 12, "WORKING_CAPITAL",
                ApplicationStatus.PROCESSING.getValue(), null, OffsetDateTime.now(), OffsetDateTime.now(),
                null, null
        );
        ApplicationEntity savedApp = applicationRepository.save(app);

        ApplicationBkiSnapshotEntity snap = new ApplicationBkiSnapshotEntity(
                null, savedApp.id(), testInn, 740, 35_000L, 3, 2, "LOW_RISK", OffsetDateTime.now()
        );
        ApplicationBkiSnapshotEntity savedSnap = bkiSnapshotRepository.save(snap);
        assertNotNull(savedSnap.id());

        ApplicationBkiSnapshotEntity fetchedSnap = bkiSnapshotRepository.findById(savedSnap.id()).orElseThrow();
        assertEquals(2, fetchedSnap.cntOverdueOver90d(), "Колонка cnt_overdue_over_90d должна корректно читаться из application_bki_snapshots");
    }
}
