package backend.repositories;

import backend.models.core.MarketplaceVendor;
import backend.models.enums.VendorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketplaceVendorRepository extends JpaRepository<MarketplaceVendor, java.util.UUID> {

    Optional<MarketplaceVendor> findByMarketplaceIdAndVendorCompanyId(java.util.UUID marketplaceId, java.util.UUID vendorCompanyId);

    Optional<MarketplaceVendor> findByIdAndMarketplaceId(java.util.UUID id, java.util.UUID marketplaceId);

    Page<MarketplaceVendor> findByMarketplaceId(java.util.UUID marketplaceId, Pageable pageable);

    Page<MarketplaceVendor> findByMarketplaceIdAndStatus(java.util.UUID marketplaceId, VendorStatus status, Pageable pageable);

    boolean existsByMarketplaceIdAndVendorCompanyId(java.util.UUID marketplaceId, java.util.UUID vendorCompanyId);

    Optional<MarketplaceVendor> findByStripeConnectAccountId(String stripeConnectAccountId);

    @Query("SELECT mv FROM MarketplaceVendor mv WHERE mv.marketplace.id = :marketplaceId AND mv.vendorCompany.id IN :vendorCompanyIds")
    List<MarketplaceVendor> findByMarketplaceIdAndVendorCompanyIdIn(
            @Param("marketplaceId") java.util.UUID marketplaceId,
            @Param("vendorCompanyIds") Collection<java.util.UUID> vendorCompanyIds);
}
