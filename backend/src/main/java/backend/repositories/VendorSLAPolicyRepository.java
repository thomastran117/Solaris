package backend.repositories;

import backend.models.core.VendorSLAPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorSLAPolicyRepository extends JpaRepository<VendorSLAPolicy, java.util.UUID> {

    List<VendorSLAPolicy> findByMarketplaceId(UUID marketplaceId);

    Optional<VendorSLAPolicy> findFirstByMarketplaceIdAndActiveTrue(UUID marketplaceId);

    List<VendorSLAPolicy> findAllByActiveTrue();
}
