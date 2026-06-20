package backend.repositories;

import backend.models.core.B2BInvoice;
import backend.models.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2BInvoiceRepository extends JpaRepository<B2BInvoice, UUID> {

    Optional<B2BInvoice> findByIdAndVendorCompanyId(UUID id, UUID vendorCompanyId);

    Optional<B2BInvoice> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    Page<B2BInvoice> findByVendorCompanyId(UUID vendorCompanyId, Pageable pageable);

    Page<B2BInvoice> findByVendorCompanyIdAndStatus(UUID vendorCompanyId, InvoiceStatus status, Pageable pageable);

    List<B2BInvoice> findByVendorCompanyIdAndStatusIn(UUID vendorCompanyId, Collection<InvoiceStatus> statuses);

    /** ISSUED invoices past their due date — used by the overdue sweep. */
    List<B2BInvoice> findByStatusAndDueDateAtBefore(InvoiceStatus status, Instant cutoff);

    /** Sum of a buyer's outstanding (unpaid) invoice balance, in cents, for credit-limit checks. */
    @Query("""
            SELECT COALESCE(SUM(i.totalCents), 0)
            FROM B2BInvoice i
            WHERE i.buyerUserId = :buyerUserId
              AND i.status IN (backend.models.enums.InvoiceStatus.ISSUED,
                               backend.models.enums.InvoiceStatus.OVERDUE)
            """)
    long sumOutstandingByBuyer(@Param("buyerUserId") UUID buyerUserId);
}
