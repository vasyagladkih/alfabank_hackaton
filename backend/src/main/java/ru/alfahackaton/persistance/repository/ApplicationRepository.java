package ru.alfahackaton.persistance.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.persistance.entity.ApplicationEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ApplicationRepository {

    private final JdbcClient jdbcClient;

    public ApplicationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ApplicationEntity save(ApplicationEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcClient.sql("""
                INSERT INTO applications (user_id, amount, term_months, purpose, status, rejection_reason, created_at, updated_at)
                VALUES (:userId, :amount, :termMonths, :purpose, :status, :rejectionReason, :createdAt, :updatedAt)
                """)
                .param("userId", entity.userId())
                .param("amount", entity.amount())
                .param("termMonths", entity.termMonths())
                .param("purpose", entity.purpose())
                .param("status", entity.status())
                .param("rejectionReason", entity.rejectionReason())
                .param("createdAt", entity.createdAt())
                .param("updatedAt", entity.updatedAt())
                .update(keyHolder, "id");

        Number key = keyHolder.getKey();
        Long generatedId = key != null ? key.longValue() : null;

        return new ApplicationEntity(
                generatedId,
                entity.userId(),
                entity.amount(),
                entity.termMonths(),
                entity.purpose(),
                entity.status(),
                entity.rejectionReason(),
                entity.createdAt(),
                entity.updatedAt(),
                entity.companyName(),
                entity.inn()
        );
    }

    public Optional<ApplicationEntity> findById(Long id) {
        return jdbcClient.sql("""
                SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                       a.created_at, a.updated_at, u.company_name, u.inn
                FROM applications a
                JOIN users u ON a.user_id = u.id
                WHERE a.id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new ApplicationEntity(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getLong("amount"),
                        rs.getInt("term_months"),
                        rs.getString("purpose"),
                        rs.getString("status"),
                        rs.getString("rejection_reason"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getString("company_name"),
                        rs.getString("inn")
                ))
                .optional();
    }

    public List<ApplicationEntity> findByUserId(Long userId) {
        return jdbcClient.sql("""
                SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                       a.created_at, a.updated_at, u.company_name, u.inn
                FROM applications a
                JOIN users u ON a.user_id = u.id
                WHERE a.user_id = :userId
                ORDER BY a.created_at DESC
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> new ApplicationEntity(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getLong("amount"),
                        rs.getInt("term_months"),
                        rs.getString("purpose"),
                        rs.getString("status"),
                        rs.getString("rejection_reason"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getString("company_name"),
                        rs.getString("inn")
                ))
                .list();
    }

    public List<ApplicationEntity> findAll(String statusFilter) {
        String sql = """
                SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                       a.created_at, a.updated_at, u.company_name, u.inn
                FROM applications a
                JOIN users u ON a.user_id = u.id
                """ + (statusFilter != null ? " WHERE a.status = :status " : "") + """
                ORDER BY a.created_at DESC
                """;

        var client = jdbcClient.sql(sql);
        if (statusFilter != null) {
            client = client.param("status", statusFilter);
        }

        return client.query((rs, rowNum) -> new ApplicationEntity(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("amount"),
                rs.getInt("term_months"),
                rs.getString("purpose"),
                rs.getString("status"),
                rs.getString("rejection_reason"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getString("company_name"),
                rs.getString("inn")
        )).list();
    }

    public void updateStatus(Long id, String status, String rejectionReason) {
        jdbcClient.sql("""
                UPDATE applications
                SET status = :status, rejection_reason = :rejectionReason, updated_at = NOW()
                WHERE id = :id
                """)
                .param("id", id)
                .param("status", status)
                .param("rejectionReason", rejectionReason)
                .update();
    }
}
