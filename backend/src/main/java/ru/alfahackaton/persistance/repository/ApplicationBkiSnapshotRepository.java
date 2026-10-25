package ru.alfahackaton.persistance.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.alfahackaton.persistance.entity.ApplicationBkiSnapshotEntity;

import java.util.Optional;

@Repository
public interface ApplicationBkiSnapshotRepository extends CrudRepository<ApplicationBkiSnapshotEntity, Long> {

    @Query("""
            SELECT * FROM application_bki_snapshots
            WHERE application_id = :applicationId
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Optional<ApplicationBkiSnapshotEntity> findLatestByApplicationId(@Param("applicationId") Long applicationId);
}