package backend.repositories;

import backend.models.core.AbandonedCartRecovery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface AbandonedCartRecoveryRepository extends JpaRepository<AbandonedCartRecovery, UUID> {

    boolean existsByUserIdAndSentDate(UUID userId, LocalDate sentDate);
}
