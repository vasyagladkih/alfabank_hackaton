package ru.alfahackaton.persistance.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.persistance.entity.ApplicationEntity;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ApplicationRepository {
    private static final String SELECT = """
            SELECT a.*, COALESCE(s.payload_json->>'companyName',u.company_name) company_name,
                   COALESCE(s.payload_json->>'inn',u.inn) inn,
                   r.rule_version, r.created_at assessed_at
            FROM applications a JOIN users u ON a.user_id=u.id
            LEFT JOIN application_snapshots s ON s.application_id=a.id
            LEFT JOIN scoring_results r ON r.application_id=a.id
            """;
    private static final RowMapper<ApplicationEntity> ROW = (rs, row) -> new ApplicationEntity(
            rs.getLong("id"), rs.getLong("user_id"), rs.getLong("amount"), rs.getInt("term_months"),
            rs.getString("purpose"), rs.getString("status"), rs.getString("rejection_reason"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
            rs.getString("company_name"), rs.getString("inn"), rs.getLong("version"), rs.getString("decision_summary"),
            rs.getBigDecimal("offered_amount"), rs.getObject("offered_term_months", Integer.class),
            rs.getBigDecimal("offered_annual_rate"), rs.getBigDecimal("offered_monthly_payment"),
            rs.getString("rule_version"), rs.getObject("assessed_at", OffsetDateTime.class));
    private final JdbcClient jdbc;

    public ApplicationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public ApplicationEntity save(ApplicationEntity entity) {
        long id = jdbc.sql("""
                INSERT INTO applications(user_id,amount,term_months,purpose,status,rejection_reason,created_at,updated_at)
                VALUES (:owner,:amount,:months,:purpose,:status,:reason,:created,:updated) RETURNING id
                """).param("owner", entity.userId()).param("amount", entity.amount()).param("months", entity.termMonths())
                .param("purpose", entity.purpose()).param("status", entity.status())
                .param("reason", entity.rejectionReason(), Types.VARCHAR).param("created", entity.createdAt())
                .param("updated", entity.updatedAt()).query(Long.class).single();
        return new ApplicationEntity(id, entity.userId(), entity.amount(), entity.termMonths(), entity.purpose(),
                entity.status(), entity.rejectionReason(), entity.createdAt(), entity.updatedAt(), entity.companyName(), entity.inn());
    }

    public Optional<ApplicationEntity> findById(Long id) {
        return jdbc.sql(SELECT + " WHERE a.id=:id").param("id", id).query(ROW).optional();
    }

    public Optional<ApplicationEntity> lockById(long id) {
        return jdbc.sql(SELECT + " WHERE a.id=:id FOR UPDATE OF a").param("id", id).query(ROW).optional();
    }

    public List<ApplicationEntity> findByUserId(Long userId) {
        return jdbc.sql(SELECT + " WHERE a.user_id=:owner ORDER BY a.created_at DESC,a.id DESC")
                .param("owner", userId).query(ROW).list();
    }

    public List<ApplicationEntity> findAll(String status) {
        var query = jdbc.sql(SELECT + (status == null ? "" : " WHERE a.status=:status")
                + " ORDER BY a.created_at DESC,a.id DESC");
        if (status != null) query = query.param("status", status);
        return query.query(ROW).list();
    }

    public boolean transition(long id, long expectedVersion, String status, String summary,
                               BigDecimal amount, Integer months, BigDecimal rate, BigDecimal payment,
                               OffsetDateTime at) {
        return jdbc.sql("""
                UPDATE applications SET status=:status,decision_summary=:summary,rejection_reason=:reason,
                    offered_amount=:amount,offered_term_months=:months,offered_annual_rate=:rate,
                    offered_monthly_payment=:payment,version=version+1,updated_at=:at
                WHERE id=:id AND version=:version
                """).param("status", status).param("summary", summary)
                .param("reason", "REJECTED".equals(status) ? summary : null, Types.VARCHAR)
                .param("amount", amount, Types.NUMERIC).param("months", months, Types.INTEGER)
                .param("rate", rate, Types.NUMERIC).param("payment", payment, Types.NUMERIC)
                .param("at", at).param("id", id).param("version", expectedVersion).update() == 1;
    }
}
