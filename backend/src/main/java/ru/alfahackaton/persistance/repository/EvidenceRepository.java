package ru.alfahackaton.persistance.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.persistance.JsonDocuments;
import ru.alfahackaton.persistance.entity.AuditRecord;
import ru.alfahackaton.persistance.entity.ScoringResultDocument;
import ru.alfahackaton.persistance.entity.SnapshotDocument;

import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Append-only storage. Transactions are owned by the calling workflow service. */
@Repository
public class EvidenceRepository {
    private final JdbcClient jdbc;

    public EvidenceRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void insertSnapshot(long applicationId, String schemaVersion, LocalDate asOf,
                               OffsetDateTime capturedAt, String origin, String payloadJson) {
        String canonical = JsonDocuments.canonicalize(payloadJson);
        jdbc.sql("""
                INSERT INTO application_snapshots(application_id,schema_version,as_of,captured_at,origin,payload_json,payload_sha256)
                VALUES (:id,:version,:asOf,:capturedAt,:origin,CAST(:payload AS jsonb),:sha)
                """).param("id", applicationId).param("version", schemaVersion).param("asOf", asOf)
                .param("capturedAt", capturedAt).param("origin", origin).param("payload", canonical)
                .param("sha", JsonDocuments.sha256(canonical)).update();
    }

    public Optional<SnapshotDocument> findSnapshot(long applicationId) {
        return jdbc.sql("SELECT * FROM application_snapshots WHERE application_id=:id").param("id", applicationId)
                .query((rs, row) -> {
                    String canonical = JsonDocuments.canonicalize(rs.getString("payload_json"));
                    String hash = rs.getString("payload_sha256");
                    if (!JsonDocuments.sha256(canonical).equals(hash)) {
                        throw new IllegalStateException("Snapshot integrity verification failed");
                    }
                    return new SnapshotDocument(rs.getLong("application_id"), rs.getString("schema_version"),
                            rs.getObject("as_of", LocalDate.class), rs.getObject("captured_at", OffsetDateTime.class),
                            rs.getString("origin"), canonical, hash);
                }).optional();
    }

    public void insertResult(long applicationId, String ruleVersion, String payloadJson, OffsetDateTime at) {
        jdbc.sql("""
                INSERT INTO scoring_results(application_id,rule_version,payload_json,created_at)
                VALUES (:id,:version,CAST(:payload AS jsonb),:at)
                """).param("id", applicationId).param("version", ruleVersion)
                .param("payload", JsonDocuments.canonicalize(payloadJson)).param("at", at).update();
    }

    public Optional<ScoringResultDocument> findResult(long applicationId) {
        return jdbc.sql("SELECT * FROM scoring_results WHERE application_id=:id").param("id", applicationId)
                .query((rs, row) -> new ScoringResultDocument(rs.getLong("application_id"), rs.getString("rule_version"),
                        JsonDocuments.canonicalize(rs.getString("payload_json")), rs.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    public void appendAudit(long applicationId, String eventKey, String eventType, String actorType,
                            Long actorUserId, String fromStatus, String toStatus, long version,
                            String rationale, String payloadJson, OffsetDateTime at) {
        jdbc.sql("""
                INSERT INTO application_audit(application_id,event_key,event_type,actor_type,actor_user_id,
                    from_status,to_status,application_version,rationale,payload_json,created_at)
                VALUES (:id,:key,:type,:actorType,:actorId,:fromStatus,:toStatus,:version,:rationale,CAST(:payload AS jsonb),:at)
                """).param("id", applicationId).param("key", eventKey).param("type", eventType)
                .param("actorType", actorType).param("actorId", actorUserId, Types.BIGINT)
                .param("fromStatus", fromStatus, Types.VARCHAR).param("toStatus", toStatus).param("version", version)
                .param("rationale", rationale).param("payload", JsonDocuments.canonicalize(payloadJson)).param("at", at).update();
    }

    public List<AuditRecord> findAudit(long applicationId) {
        return jdbc.sql("SELECT * FROM application_audit WHERE application_id=:id ORDER BY created_at,id")
                .param("id", applicationId).query((rs, row) -> new AuditRecord(rs.getLong("id"), rs.getLong("application_id"),
                        rs.getString("event_key"), rs.getString("event_type"), rs.getString("actor_type"),
                        rs.getObject("actor_user_id", Long.class), rs.getString("from_status"), rs.getString("to_status"),
                        rs.getLong("application_version"), rs.getString("rationale"),
                        JsonDocuments.canonicalize(rs.getString("payload_json")), rs.getObject("created_at", OffsetDateTime.class))).list();
    }
}
