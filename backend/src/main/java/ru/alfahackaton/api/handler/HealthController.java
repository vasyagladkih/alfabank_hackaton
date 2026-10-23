package ru.alfahackaton.api.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.web.bind.annotation.RestController;
import ru.alfahackaton.api.HealthApi;
import ru.alfahackaton.dto.HealthResponse;

@RestController
public class HealthController implements HealthApi {
    private final JdbcClient jdbc;
    private final ApplicationAvailability availability;
    public HealthController(JdbcClient jdbc, ApplicationAvailability availability) {
        this.jdbc = jdbc;
        this.availability = availability;
    }

    @Override
    public ResponseEntity<HealthResponse> getHealth() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.status(503).body(new HealthResponse(HealthResponse.StatusEnum.DOWN));
        }
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            return ResponseEntity.ok(new HealthResponse(HealthResponse.StatusEnum.UP));
        } catch (RuntimeException unavailable) {
            return ResponseEntity.status(503).body(new HealthResponse(HealthResponse.StatusEnum.DOWN));
        }
    }
}
