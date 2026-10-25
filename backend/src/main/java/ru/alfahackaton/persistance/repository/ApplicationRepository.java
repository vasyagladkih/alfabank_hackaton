package ru.alfahackaton.persistance.repository;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.persistance.entity.ApplicationEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends CrudRepository<ApplicationEntity, Long> {

    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.review_reason_type, a.scoring_score, a.calculated_pdn_percent, a.max_limit_rub,
                   a.approved_amount, a.approved_rate, a.created_at, a.updated_at,
                   u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            WHERE a.id = :id
            """)
    Optional<ApplicationEntity> findWithUserById(@Param("id") Long id);

    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.review_reason_type, a.scoring_score, a.calculated_pdn_percent, a.max_limit_rub,
                   a.approved_amount, a.approved_rate, a.created_at, a.updated_at,
                   u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            WHERE a.id = :id AND a.user_id = :userId
            """)
    Optional<ApplicationEntity> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.review_reason_type, a.scoring_score, a.calculated_pdn_percent, a.max_limit_rub,
                   a.approved_amount, a.approved_rate, a.created_at, a.updated_at,
                   u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            WHERE a.user_id = :userId
            ORDER BY a.created_at DESC
            """)
    List<ApplicationEntity> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.review_reason_type, a.scoring_score, a.calculated_pdn_percent, a.max_limit_rub,
                   a.approved_amount, a.approved_rate, a.created_at, a.updated_at,
                   u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            ORDER BY a.created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<ApplicationEntity> findAllPaged(@Param("limit") int limit, @Param("offset") long offset);

    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.review_reason_type, a.scoring_score, a.calculated_pdn_percent, a.max_limit_rub,
                   a.approved_amount, a.approved_rate, a.created_at, a.updated_at,
                   u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            WHERE a.status = :status
            ORDER BY a.created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<ApplicationEntity> findByStatusPaged(@Param("status") String status, @Param("limit") int limit, @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM applications WHERE status = :status")
    long countByStatus(@Param("status") String status);

    @Modifying
    @Query("""
            UPDATE applications
            SET status = :status,
                rejection_reason = :rejectionReason,
                review_reason_type = :reviewReasonType,
                scoring_score = :scoringScore,
                calculated_pdn_percent = :calculatedPdnPercent,
                max_limit_rub = :maxLimitRub,
                approved_amount = :approvedAmount,
                approved_rate = :approvedRate,
                updated_at = NOW()
            WHERE id = :id
            """)
    void updateDecision(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("rejectionReason") String rejectionReason,
            @Param("reviewReasonType") String reviewReasonType,
            @Param("scoringScore") Integer scoringScore,
            @Param("calculatedPdnPercent") Double calculatedPdnPercent,
            @Param("maxLimitRub") Long maxLimitRub,
            @Param("approvedAmount") Long approvedAmount,
            @Param("approvedRate") Double approvedRate
    );

    @Modifying
    @Query("""
            UPDATE applications
            SET status = :status,
                rejection_reason = :rejectionReason,
                review_reason_type = :reviewReasonType,
                updated_at = NOW()
            WHERE id = :id
            """)
    void updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("rejectionReason") String rejectionReason,
            @Param("reviewReasonType") String reviewReasonType
    );
}
