package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import backend.models.core.InventoryAdjustment;

@Repository
public interface InventoryAdjustmentRepository
        extends JpaRepository<InventoryAdjustment, java.util.UUID>,
                JpaSpecificationExecutor<InventoryAdjustment> {

    Page<InventoryAdjustment> findAllByProductIdAndProductCompanyId(java.util.UUID productId, java.util.UUID companyId, Pageable pageable);
}
