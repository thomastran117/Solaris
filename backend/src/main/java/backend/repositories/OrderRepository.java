package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.Order;
import backend.models.enums.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findAllByUserId(long userId, Pageable pageable);
    Optional<Order> findByIdAndUserId(long id, long userId);
    Optional<Order> findByPaymentIntentId(String paymentIntentId);
    Page<Order> findAllByUserIdAndStatus(long userId, OrderStatus status, Pageable pageable);
    List<Order> findAllByStatusAndCompensatedFalseAndCreatedAtBefore(OrderStatus status, Instant before);
}
