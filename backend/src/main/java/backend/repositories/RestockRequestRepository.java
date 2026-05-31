package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.models.core.RestockRequest;
import backend.models.enums.RestockStatus;

import java.util.Optional;

@Repository
public interface RestockRequestRepository extends JpaRepository<RestockRequest, java.util.UUID> {

    Page<RestockRequest> findAllByCompanyId(java.util.UUID companyId, Pageable pageable);

    Page<RestockRequest> findAllByCompanyIdAndStatus(java.util.UUID companyId, RestockStatus status, Pageable pageable);

    Page<RestockRequest> findAllByCompanyIdAndProductId(java.util.UUID companyId, java.util.UUID productId, Pageable pageable);

    Page<RestockRequest> findAllByCompanyIdAndStatusAndProductId(java.util.UUID companyId, RestockStatus status, java.util.UUID productId, Pageable pageable);

    Optional<RestockRequest> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    /** Guards against creating duplicate auto-restock requests for a product (no variant). */
    boolean existsByProductIdAndVariantIsNullAndStatusIn(java.util.UUID productId, java.util.Collection<RestockStatus> statuses);

    /** Guards against creating duplicate auto-restock requests for a specific variant. */
    boolean existsByProductIdAndVariantIdAndStatusIn(java.util.UUID productId, java.util.UUID variantId, java.util.Collection<RestockStatus> statuses);
}
