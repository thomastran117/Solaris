package backend.repositories;

import backend.models.core.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, java.util.UUID> {

    List<OrderItem> findAllByOrderId(java.util.UUID orderId);

    List<OrderItem> findAllBySubOrderId(java.util.UUID subOrderId);

    // Referential-integrity guards for deletion: an entity referenced by any order line (current
    // or historical) cannot be physically deleted without orphaning order/reporting data.
    boolean existsByProductId(java.util.UUID productId);

    boolean existsByVariantId(java.util.UUID variantId);

    @Modifying
    @Query("UPDATE OrderItem i SET i.subOrderId = :subOrderId WHERE i.id IN :itemIds")
    void setSubOrderId(@Param("subOrderId") java.util.UUID subOrderId, @Param("itemIds") Collection<java.util.UUID> itemIds);
}
