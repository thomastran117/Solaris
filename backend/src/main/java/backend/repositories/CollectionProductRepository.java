package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.CollectionProduct;
import backend.models.enums.CollectionMembershipSource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionProductRepository extends JpaRepository<CollectionProduct, Long> {

    Optional<CollectionProduct> findByCollectionIdAndProductId(long collectionId, long productId);

    boolean existsByCollectionIdAndProductId(long collectionId, long productId);

    long countByCollectionId(long collectionId);

    List<CollectionProduct> findAllByCollectionId(long collectionId);

    List<CollectionProduct> findAllByCollectionIdAndSource(long collectionId, CollectionMembershipSource source);

    /** Used by the collection-page listing — ranked by pinnedRank asc (nulls last), then id desc. */
    @Query("SELECT cp FROM CollectionProduct cp JOIN FETCH cp.product p JOIN FETCH p.company "
            + "WHERE cp.collection.id = :collectionId "
            + "ORDER BY CASE WHEN cp.pinnedRank IS NULL THEN 1 ELSE 0 END, cp.pinnedRank ASC, cp.id DESC")
    Page<CollectionProduct> findAllByCollectionIdRanked(@Param("collectionId") long collectionId, Pageable pageable);

    /**
     * Used by the indexing pipeline to attach the set of collection IDs each product belongs to.
     * Returned shape is {@code [productId, collectionId]} so the caller can group by product.
     */
    @Query("SELECT cp.product.id, cp.collection.id FROM CollectionProduct cp "
            + "WHERE cp.product.id IN :productIds AND cp.collection.status = backend.models.enums.CollectionStatus.ACTIVE")
    List<Object[]> findProductCollectionPairs(@Param("productIds") Collection<Long> productIds);

    /**
     * Deletes all AUTO rows for a collection — used by {@code DynamicCollectionWorker} at the
     * start of a rule refresh. MANUAL rows are preserved so that hand-picked extras remain.
     */
    @Modifying
    @Query("DELETE FROM CollectionProduct cp WHERE cp.collection.id = :collectionId AND cp.source = :source")
    int deleteByCollectionIdAndSource(@Param("collectionId") long collectionId,
                                      @Param("source") CollectionMembershipSource source);

    /** Removes a product from every collection — used when a product is deleted. */
    @Modifying
    @Query("DELETE FROM CollectionProduct cp WHERE cp.product.id = :productId")
    int deleteAllByProductId(@Param("productId") long productId);
}
