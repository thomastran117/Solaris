package backend.repositories;

import backend.models.core.StockNotification;
import backend.models.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockNotificationRepository extends JpaRepository<StockNotification, java.util.UUID> {

    List<StockNotification> findAllByProductIdAndVariantRefAndStatus(
            java.util.UUID productId, java.util.UUID variantRef, NotificationStatus status);

    Optional<StockNotification> findByUserIdAndProductIdAndVariantRef(
            UUID userId, java.util.UUID productId, java.util.UUID variantRef);

    List<StockNotification> findAllByUserIdAndStatusNot(UUID userId, NotificationStatus status);
}
