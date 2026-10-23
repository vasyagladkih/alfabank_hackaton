package ru.alfahackaton.persistance.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class ProcessingJobRepository {
    private final JdbcClient jdbc;
    public ProcessingJobRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void enqueue(long applicationId, OffsetDateTime at) {
        jdbc.sql("INSERT INTO processing_jobs(application_id,available_at) VALUES (:id,:at) ON CONFLICT(application_id) DO NOTHING")
                .param("id", applicationId).param("at", at).update();
    }

    public Optional<Long> lockNextReady(OffsetDateTime now) {
        return jdbc.sql("""
                SELECT a.id FROM applications a JOIN processing_jobs j ON j.application_id=a.id
                WHERE a.status='PROCESSING' AND j.completed_at IS NULL AND j.available_at<=:now
                ORDER BY j.available_at,a.id LIMIT 1 FOR UPDATE OF a,j SKIP LOCKED
                """).param("now", now).query(Long.class).optional();
    }

    public void complete(long applicationId, OffsetDateTime at) {
        jdbc.sql("UPDATE processing_jobs SET completed_at=:at,last_error_code=NULL WHERE application_id=:id")
                .param("id", applicationId).param("at", at).update();
    }

    public int recordFailure(long applicationId, String errorCode, OffsetDateTime nextAttempt) {
        return jdbc.sql("""
                UPDATE processing_jobs SET attempts=attempts+1,last_error_code=:code,available_at=:next
                WHERE application_id=:id AND completed_at IS NULL RETURNING attempts
                """).param("id", applicationId).param("code", errorCode).param("next", nextAttempt)
                .query(Integer.class).single();
    }
}
