package backend.repositories;

import backend.models.core.ProductChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductChangeLogRepository
        extends JpaRepository<ProductChangeLog, UUID>,
                JpaSpecificationExecutor<ProductChangeLog> {

    Page<ProductChangeLog> findAllByProductIdAndCompanyId(UUID productId, UUID companyId, Pageable pageable);

    List<ProductChangeLog> findAllByIdInAndCompanyId(List<UUID> ids, UUID companyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProductChangeLog p WHERE p.product.id = :productId")
    void deleteAllByProductId(@Param("productId") UUID productId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProductChangeLog p WHERE p.variant.id = :variantId")
    void deleteAllByVariantId(@Param("variantId") UUID variantId);
}
