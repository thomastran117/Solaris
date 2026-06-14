package backend.repositories;

import backend.models.core.ProductBundle;
import backend.models.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BundleRepository extends JpaRepository<ProductBundle, java.util.UUID> {
    Optional<ProductBundle> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);
    List<ProductBundle> findAllByIdInAndCompanyId(List<java.util.UUID> ids, java.util.UUID companyId);
    List<ProductBundle> findAllByCompanyId(java.util.UUID companyId);
    Page<ProductBundle> findAllByCompanyId(java.util.UUID companyId, Pageable pageable);
    Page<ProductBundle> findAllByCompanyIdAndStatus(java.util.UUID companyId, ProductStatus status, Pageable pageable);
    /** Public storefront listing: only ACTIVE + listed bundles are browsable by non-members. */
    Page<ProductBundle> findAllByCompanyIdAndStatusAndListed(java.util.UUID companyId, ProductStatus status, boolean listed, Pageable pageable);
    boolean existsByItemsProductId(java.util.UUID productId);
}
