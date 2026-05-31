package backend.repositories;

import backend.models.core.ProductSimilarity;
import backend.models.core.ProductSimilarityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductSimilarityRepository extends JpaRepository<ProductSimilarity, ProductSimilarityId> {

    List<ProductSimilarity> findTop10ByIdSourceProductIdOrderByScoreDesc(UUID sourceProductId);

    void deleteAllByIdSourceProductId(UUID sourceProductId);

    /** Used by the nightly worker to find which source IDs already have recently computed rows. */
    @Query("SELECT DISTINCT ps.id.sourceProductId FROM ProductSimilarity ps WHERE ps.id.sourceProductId IN :ids AND ps.computedAt >= :threshold")
    List<UUID> findSourceIdsWithFreshRows(@Param("ids") List<UUID> ids, @Param("threshold") Instant threshold);
}
