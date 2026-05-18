package backend.repositories;

import backend.models.core.VendorSLABreach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VendorSLABreachRepository extends JpaRepository<VendorSLABreach, java.util.UUID> {

    Page<VendorSLABreach> findByVendorId(UUID vendorId, Pageable pageable);

    List<VendorSLABreach> findByVendorIdAndResolvedAtIsNull(UUID vendorId);

    Page<VendorSLABreach> findByPolicyId(UUID policyId, Pageable pageable);

    long countByVendorIdAndResolvedAtIsNull(UUID vendorId);
}
