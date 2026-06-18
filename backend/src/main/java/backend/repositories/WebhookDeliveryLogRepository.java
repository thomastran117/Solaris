package backend.repositories;

import backend.models.core.WebhookDeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {

    @Query("select l from WebhookDeliveryLog l where l.subscription.id = :subscriptionId " +
           "and l.createdAt >= :since order by l.createdAt desc")
    Page<WebhookDeliveryLog> findBySubscriptionIdSince(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("since") Instant since,
            Pageable pageable);
}
