package backend.repositories;

import backend.models.core.Subscription;
import backend.models.enums.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, java.util.UUID> {

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.stripeSubscriptionId = :stripeSubscriptionId")
    Optional<Subscription> findByStripeSubscriptionIdForUpdate(@Param("stripeSubscriptionId") String stripeSubscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.id = :id AND s.user.id = :userId")
    Optional<Subscription> findByIdAndUserIdForUpdate(@Param("id") java.util.UUID id, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"items"})
    List<Subscription> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Subscription> findByIdAndUserId(java.util.UUID id, UUID userId);

    List<Subscription> findAllByStatusAndNextBillingAtBetween(
            SubscriptionStatus status, Instant from, Instant to);

    List<Subscription> findAllByStatusAndPastDueSinceBefore(SubscriptionStatus status, Instant cutoff);
}
