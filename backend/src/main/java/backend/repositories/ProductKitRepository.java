package backend.repositories;

import backend.models.core.ProductKit;
import backend.models.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductKitRepository extends JpaRepository<ProductKit, UUID> {
    Optional<ProductKit> findByIdAndCompanyId(UUID id, UUID companyId);
    List<ProductKit> findAllByIdInAndCompanyId(List<UUID> ids, UUID companyId);
    Page<ProductKit> findAllByCompanyId(UUID companyId, Pageable pageable);
    Page<ProductKit> findAllByCompanyIdAndStatus(UUID companyId, ProductStatus status, Pageable pageable);
    /** Public storefront listing: only ACTIVE + listed kits are browsable by non-members. */
    Page<ProductKit> findAllByCompanyIdAndStatusAndListed(UUID companyId, ProductStatus status, boolean listed, Pageable pageable);
}
