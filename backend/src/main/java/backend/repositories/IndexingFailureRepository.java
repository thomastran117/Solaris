package backend.repositories;

import backend.models.core.IndexingFailure;
import backend.models.enums.IndexingFailureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndexingFailureRepository extends JpaRepository<IndexingFailure, Long> {
    List<IndexingFailure> findAllByStatusAndAttemptsLessThan(IndexingFailureStatus status, int maxAttempts);

    /** Used by the heartbeat scheduler so an unfixed search drift is visible in logs/metrics. */
    long countByStatus(IndexingFailureStatus status);
}
