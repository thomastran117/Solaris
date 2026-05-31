package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.OrderCompensation;
import backend.models.enums.CompensationStatus;

import java.util.List;

@Repository
public interface OrderCompensationRepository extends JpaRepository<OrderCompensation, Long> {
    List<OrderCompensation> findAllByOrderId(long orderId);
    List<OrderCompensation> findAllByStatus(CompensationStatus status);
    List<OrderCompensation> findAllByStatusAndAttemptsLessThan(CompensationStatus status, int maxAttempts);
}
