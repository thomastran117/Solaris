package backend.repositories;

import backend.models.core.FailedPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface FailedPaymentAttemptRepository extends JpaRepository<FailedPaymentAttempt, UUID> {

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    long countByIpAndCreatedAtAfter(String ip, Instant since);
}
