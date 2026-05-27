package backend.repositories;

import backend.models.core.OrderKitSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderKitSelectionRepository extends JpaRepository<OrderKitSelection, UUID> {
    List<OrderKitSelection> findAllByOrderItemId(UUID orderItemId);
}
