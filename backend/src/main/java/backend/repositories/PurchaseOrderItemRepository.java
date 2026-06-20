package backend.repositories;

import backend.models.core.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {
    List<PurchaseOrderItem> findAllByPurchaseOrderId(UUID purchaseOrderId);
    List<PurchaseOrderItem> findAllByPurchaseOrderIdIn(Collection<UUID> purchaseOrderIds);
    Optional<PurchaseOrderItem> findByIdAndPurchaseOrderId(UUID id, UUID purchaseOrderId);

    /**
     * Atomically increments receivedQty only if doing so does not exceed orderedQty.
     * Returns the number of rows updated (1 on success, 0 if the guard rejected the
     * increment — i.e. a concurrent receipt would have caused over-receipt). This is
     * the concurrency-safety mechanism for receipts, in place of optimistic @Version.
     */
    @Modifying
    @Query("UPDATE PurchaseOrderItem i SET i.receivedQty = i.receivedQty + :delta " +
           "WHERE i.id = :id AND (i.receivedQty + :delta) <= i.orderedQty")
    int incrementReceivedQty(@Param("id") UUID id, @Param("delta") int delta);
}
