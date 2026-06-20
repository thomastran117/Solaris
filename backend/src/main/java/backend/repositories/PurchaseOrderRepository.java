package backend.repositories;

import backend.models.core.PurchaseOrder;
import backend.models.enums.POStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    @EntityGraph(attributePaths = {"supplier"})
    Page<PurchaseOrder> findAllByCompanyId(UUID companyId, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier"})
    Page<PurchaseOrder> findAllByCompanyIdAndStatus(UUID companyId, POStatus status, Pageable pageable);

    Optional<PurchaseOrder> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsBySupplierId(UUID supplierId);
}
