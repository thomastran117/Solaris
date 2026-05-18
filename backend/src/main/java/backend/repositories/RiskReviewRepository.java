package backend.repositories;

import backend.models.core.RiskReview;
import backend.models.enums.RiskReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskReviewRepository extends JpaRepository<RiskReview, UUID> {

    Optional<RiskReview> findByOrderId(UUID orderId);

    /**
     * Paginated queue of reviews for a given company.
     * Only returns reviews whose held order belongs exclusively to that company.
     */
    @Query("SELECT rr FROM RiskReview rr " +
           "WHERE rr.status = :status " +
           "AND EXISTS (" +
           "  SELECT oi.id FROM Order o JOIN o.items oi " +
           "  WHERE o.id = rr.orderId " +
           "  AND ((oi.product IS NOT NULL AND oi.product.company.id = :companyId) " +
           "    OR (oi.bundle IS NOT NULL AND oi.bundle.company.id = :companyId))" +
           ") " +
           "AND NOT EXISTS (" +
           "  SELECT oi2.id FROM Order o2 JOIN o2.items oi2 " +
           "  WHERE o2.id = rr.orderId " +
           "  AND ((oi2.product IS NOT NULL AND oi2.product.company.id <> :companyId) " +
           "    OR (oi2.bundle IS NOT NULL AND oi2.bundle.company.id <> :companyId) " +
           "    OR (oi2.product IS NULL AND oi2.bundle IS NULL))" +
           ")")
    Page<RiskReview> findByCompanyIdAndStatus(
            @Param("companyId") UUID companyId,
            @Param("status") RiskReviewStatus status,
            Pageable pageable);
}
