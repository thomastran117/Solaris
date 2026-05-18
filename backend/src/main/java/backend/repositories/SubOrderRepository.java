package backend.repositories;

import backend.models.core.SubOrder;
import backend.models.enums.SubOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubOrderRepository extends JpaRepository<SubOrder, java.util.UUID> {

    List<SubOrder> findAllByOrderId(java.util.UUID orderId);

    Page<SubOrder> findByMarketplaceVendorId(java.util.UUID marketplaceVendorId, Pageable pageable);

    Page<SubOrder> findByMarketplaceVendorIdAndStatus(java.util.UUID marketplaceVendorId, SubOrderStatus status, Pageable pageable);

    Optional<SubOrder> findByIdAndMarketplaceVendorId(java.util.UUID id, java.util.UUID marketplaceVendorId);

    @Query("SELECT s FROM SubOrder s WHERE s.order.id = :orderId AND s.marketplaceVendor.id = :vendorId")
    Optional<SubOrder> findByOrderIdAndMarketplaceVendorId(
            @Param("orderId") java.util.UUID orderId,
            @Param("vendorId") java.util.UUID vendorId);
}
