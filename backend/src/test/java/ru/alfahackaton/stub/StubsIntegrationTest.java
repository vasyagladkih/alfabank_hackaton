package ru.alfahackaton.stub;

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
import ru.alfahackaton.persistance.repository.ApplicationBkiSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationFnsSnapshotRepository;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;
import ru.alfahackaton.stub.bki.entity.MockBkiEntity;
import ru.alfahackaton.stub.bki.repository.MockBkiRepository;
import ru.alfahackaton.stub.fns.entity.MockFnsEntity;
import ru.alfahackaton.stub.fns.repository.MockFnsRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(StubsIntegrationTest.TestConfig.class)
class StubsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MockFnsRepository fnsRepository;

    @MockitoBean
    private MockBkiRepository bkiRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ApplicationRepository applicationRepository;

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
    @DisplayName("Стаб ФНС доступен без токена авторизации и возвращает статус организации")
    void testFnsStubPermitAll() throws Exception {
        MockFnsEntity entity = new MockFnsEntity("7701234567", "ООО \"Ромашка\"", "ACTIVE", false, LocalDate.now().minusYears(3));
        Mockito.when(fnsRepository.findById("7701234567")).thenReturn(Optional.of(entity));

        mockMvc.perform(post("/api/v1/stubs/fns/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inn\":\"7701234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inn").value("7701234567"))
                .andExpect(jsonPath("$.stateStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.hasTaxBlocks").value(false));
    }

    @Test
    @DisplayName("Стаб ФНС: при отсутствии ИНН возвращает 400 Bad Request, при ненайденном ИНН возвращает 404")
    void testFnsStubErrorCases() throws Exception {
        // Пустой ИНН
        mockMvc.perform(post("/api/v1/stubs/fns/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inn\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Ненайденный ИНН
        Mockito.when(fnsRepository.findById("9999999999")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/v1/stubs/fns/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inn\":\"9999999999\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Стаб БКИ доступен без токена авторизации и возвращает скоринг и платежи")
    void testBkiStubPermitAll() throws Exception {
        MockBkiEntity entity = new MockBkiEntity("7701234567", 750, 50_000L, 0, 0);
        Mockito.when(bkiRepository.findById("7701234567")).thenReturn(Optional.of(entity));

        mockMvc.perform(post("/api/v1/stubs/bki/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inn\":\"7701234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inn").value("7701234567"))
                .andExpect(jsonPath("$.bkiScore").value(750))
                .andExpect(jsonPath("$.monthlyPaymentRub").value(50000));
    }

    @Test
    @DisplayName("Стаб БКИ: при отсутствии ИНН возвращает 400 Bad Request, при ненайденном ИНН возвращает 404")
    void testBkiStubErrorCases() throws Exception {
        mockMvc.perform(post("/api/v1/stubs/bki/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inn\":\"\"}"))
                .andExpect(status().isBadRequest());

        Mockito.when(bkiRepository.findById("9999999999")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/v1/stubs/bki/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inn\":\"9999999999\"}"))
                .andExpect(status().isNotFound());
    }
}
