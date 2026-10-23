package ru.alfahackaton.persistance.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.persistance.JsonDocuments;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Immutable command receipts, scoped to an application and authenticated actor. */
@Repository
public class MutationRequestRepository {
    public record Receipt(String commandSha256, String responseJson) {}
    private final JdbcClient jdbc;

    public MutationRequestRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Receipt> find(long applicationId, long actorId, UUID requestId) {
        return jdbc.sql("""
                SELECT command_sha256,response_json FROM mutation_requests
                WHERE application_id=:application AND actor_user_id=:actor AND request_id=:request
                """).param("application", applicationId).param("actor", actorId).param("request", requestId)
                .query((rs, row) -> new Receipt(rs.getString("command_sha256"),
                        JsonDocuments.canonicalize(rs.getString("response_json")))).optional();
    }

    public void insert(long applicationId, long actorId, UUID requestId, String commandSha256,
                       String responseJson, OffsetDateTime at) {
        jdbc.sql("""
                INSERT INTO mutation_requests(application_id,actor_user_id,request_id,command_sha256,response_json,created_at)
                VALUES (:application,:actor,:request,:sha,CAST(:response AS jsonb),:at)
                """).param("application", applicationId).param("actor", actorId).param("request", requestId)
                .param("sha", commandSha256).param("response", JsonDocuments.canonicalize(responseJson)).param("at", at).update();
    }
}
