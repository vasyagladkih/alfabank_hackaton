package ru.alfahackaton;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.stub.authservice.service.StubTokenService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AlfahackatonApplicationTests.TestConfig.class)
class AlfahackatonApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTokenService stubTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ApplicationRepository applicationRepository;

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
    @DisplayName("Контекст приложения Spring Boot успешно поднимается")
    void contextLoads() {
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
        Mockito.when(applicationRepository.findByUserId(101L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/client/applications")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk());
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
