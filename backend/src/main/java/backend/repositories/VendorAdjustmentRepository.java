package backend.repositories;

import backend.models.core.VendorAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VendorAdjustmentRepository extends JpaRepository<VendorAdjustment, java.util.UUID> {

    List<VendorAdjustment> findAllByVendorIdAndAppliedToPayoutIdIsNull(UUID vendorId);

    List<VendorAdjustment> findAllByVendorId(UUID vendorId);
}
