package ru.alfahackaton;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.persistance.repository.UserRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@SpringBootTest
@Import(AlfahackatonApplicationTests.TestConfig.class)
class AlfahackatonApplicationTests {

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
    void contextLoads() {
    }

}
