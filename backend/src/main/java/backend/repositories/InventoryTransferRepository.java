package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.InventoryTransfer;
import backend.models.enums.TransferStatus;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, UUID> {

    @EntityGraph(attributePaths = {"product", "fromLocation", "toLocation"})
    Optional<InventoryTransfer> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * Lists a company's transfers, optionally filtered by status and/or a location that is either
     * the source or destination. Null filters are ignored. Order is controlled by the Pageable.
     *
     * <p>The {@link EntityGraph} fetch-joins product/from/to so the response mapping does not fire
     * a SELECT per row for their names (all are {@code @ManyToOne}, so this is pagination-safe).
     */
    @EntityGraph(attributePaths = {"product", "fromLocation", "toLocation"})
    @Query("SELECT t FROM InventoryTransfer t WHERE t.company.id = :companyId " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:locationId IS NULL OR t.fromLocation.id = :locationId OR t.toLocation.id = :locationId)")
    Page<InventoryTransfer> findAllByCompanyFiltered(
            @Param("companyId") UUID companyId,
            @Param("status") TransferStatus status,
            @Param("locationId") UUID locationId,
            Pageable pageable);
}
