package backend.repositories;

import backend.models.core.VendorPayout;
import backend.models.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorPayoutRepository extends JpaRepository<VendorPayout, java.util.UUID> {

    Page<VendorPayout> findByVendorId(UUID vendorId, Pageable pageable);

    Page<VendorPayout> findByVendorIdAndStatus(UUID vendorId, PayoutStatus status, Pageable pageable);

    Optional<VendorPayout> findByStripeTransferId(String stripeTransferId);

    List<VendorPayout> findAllByStatus(PayoutStatus status);

    Optional<VendorPayout> findByIdAndVendorId(java.util.UUID id, UUID vendorId);

    @Query("SELECT p FROM VendorPayout p WHERE p.vendorId = :vendorId AND p.status = :status")
    List<VendorPayout> findByVendorIdAndStatusList(
            @Param("vendorId") UUID vendorId, @Param("status") PayoutStatus status);
}
