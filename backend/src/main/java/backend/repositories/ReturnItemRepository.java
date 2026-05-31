package backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.ReturnItem;

import java.util.UUID;

@Repository
public interface ReturnItemRepository extends JpaRepository<ReturnItem, UUID> {

    /**
     * Sum of all queued or approved returned quantities for a given order item.
     * Excludes REJECTED and FAILED returns so those quantities become returnable again.
     * Used to enforce the constraint: totalQueuedQty + newQty ≤ orderItem.quantity.
     */
    @Query("SELECT COALESCE(SUM(ri.quantityReturned), 0) FROM ReturnItem ri " +
           "WHERE ri.orderItem.id = :orderItemId " +
           "AND ri.returnRequest.status NOT IN ('REJECTED','FAILED')")
    int sumReturnedQuantityByOrderItemId(@Param("orderItemId") UUID orderItemId);
}
