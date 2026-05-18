package backend.repositories;

import backend.models.core.SubscriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionItemRepository extends JpaRepository<SubscriptionItem, java.util.UUID> {
    List<SubscriptionItem> findAllBySubscriptionId(java.util.UUID subscriptionId);
}
