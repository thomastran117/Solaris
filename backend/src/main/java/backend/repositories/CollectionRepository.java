package backend.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import backend.models.core.Collection;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, java.util.UUID> {

    Optional<Collection> findByIdAndCompanyId(java.util.UUID id, java.util.UUID companyId);

    Optional<Collection> findBySlugAndCompanyId(String slug, java.util.UUID companyId);

    boolean existsBySlugAndCompanyId(String slug, java.util.UUID companyId);

    Page<Collection> findAllByCompanyId(java.util.UUID companyId, Pageable pageable);

    Page<Collection> findAllByCompanyIdAndType(java.util.UUID companyId, CollectionType type, Pageable pageable);

    Page<Collection> findAllByCompanyIdAndStatus(java.util.UUID companyId, CollectionStatus status, Pageable pageable);

    /**
     * Featured collections, ordered by {@code featuredRank} asc (nulls last). Used by the
     * marketplace home page and vendor storefront sections.
     */
    @Query("SELECT c FROM Collection c JOIN FETCH c.company co "
            + "WHERE co.id IN (SELECT p.company.id FROM Product p WHERE p.marketplaceId = :marketplaceId AND p.marketplaceListed = true) "
            + "AND c.featured = true AND c.status = backend.models.enums.CollectionStatus.ACTIVE "
            + "ORDER BY CASE WHEN c.featuredRank IS NULL THEN 1 ELSE 0 END, c.featuredRank ASC, c.id ASC")
    List<Collection> findFeaturedForMarketplace(@Param("marketplaceId") java.util.UUID marketplaceId);

    /**
     * Featured collections for one vendor inside one marketplace — drives the vendor
     * storefront's featured-collections row.
     */
    @Query("SELECT c FROM Collection c WHERE c.company.id = :vendorCompanyId "
            + "AND c.featured = true AND c.status = backend.models.enums.CollectionStatus.ACTIVE "
            + "ORDER BY CASE WHEN c.featuredRank IS NULL THEN 1 ELSE 0 END, c.featuredRank ASC, c.id ASC")
    List<Collection> findFeaturedForVendor(@Param("vendorCompanyId") java.util.UUID vendorCompanyId);

    /**
     * Used by {@code DynamicCollectionWorker} to find dynamic collections eligible for a
     * materialisation pass. Filtering on status keeps the worker from refreshing
     * drafts/archives.
     */
    List<Collection> findAllByTypeAndStatus(CollectionType type, CollectionStatus status);
}
