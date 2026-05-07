package backend.repositories;

import backend.models.core.StockNotification;
import backend.models.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockNotificationRepository extends JpaRepository<StockNotification, Long> {

    List<StockNotification> findAllByProductIdAndVariantRefAndStatus(
            Long productId, long variantRef, NotificationStatus status);

    Optional<StockNotification> findByUserIdAndProductIdAndVariantRef(
            Long userId, Long productId, long variantRef);

    List<StockNotification> findAllByUserIdAndStatusNot(Long userId, NotificationStatus status);
}
