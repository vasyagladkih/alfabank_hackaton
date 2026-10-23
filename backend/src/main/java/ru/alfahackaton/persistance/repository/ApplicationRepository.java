package ru.alfahackaton.persistance.repository;

import org.jspecify.annotations.NonNull;
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

    @Override
    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.created_at, a.updated_at, u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            WHERE a.id = :id
            """)
    Optional<ApplicationEntity> findById(@Param("id") @NonNull Long id);

    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.created_at, a.updated_at, u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            WHERE a.user_id = :userId
            ORDER BY a.created_at DESC
            """)
    List<ApplicationEntity> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.created_at, a.updated_at, u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            ORDER BY a.created_at DESC
            """)
    List<ApplicationEntity> findAllWithUser();

    @Query("""
            SELECT a.id, a.user_id, a.amount, a.term_months, a.purpose, a.status, a.rejection_reason,
                   a.created_at, a.updated_at, u.company_name, u.inn
            FROM applications a
            JOIN users u ON a.user_id = u.id
            WHERE a.status = :status
            ORDER BY a.created_at DESC
            """)
    List<ApplicationEntity> findByStatusWithUser(@Param("status") String status);

    default List<ApplicationEntity> findAll(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return findAllWithUser();
        }
        return findByStatusWithUser(statusFilter);
    }

    @Modifying
    @Query("""
            UPDATE applications
            SET status = :status, rejection_reason = :rejectionReason, updated_at = NOW()
            WHERE id = :id
            """)
    void updateStatus(@Param("id") Long id, @Param("status") String status, @Param("rejectionReason") String rejectionReason);
}
