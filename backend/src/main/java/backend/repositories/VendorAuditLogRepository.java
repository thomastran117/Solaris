package backend.repositories;

import backend.models.core.VendorAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorAuditLogRepository extends JpaRepository<VendorAuditLog, java.util.UUID> {

    Page<VendorAuditLog> findByMarketplaceVendorIdOrderByCreatedAtDesc(java.util.UUID marketplaceVendorId, Pageable pageable);
}
