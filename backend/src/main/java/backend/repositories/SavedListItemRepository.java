package backend.repositories;

import backend.models.core.SavedListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavedListItemRepository extends JpaRepository<SavedListItem, java.util.UUID> {

    Optional<SavedListItem> findByIdAndSavedListId(java.util.UUID id, java.util.UUID savedListId);

    /** Duplicate check when variant is specified. */
    boolean existsBySavedListIdAndProductIdAndVariantId(java.util.UUID savedListId, java.util.UUID productId, java.util.UUID variantId);

    /** Duplicate check when no variant is specified. */
    @Query("""
            SELECT COUNT(i) > 0 FROM SavedListItem i
            WHERE i.savedList.id = :savedListId
              AND i.product.id = :productId
              AND i.variant IS NULL
            """)
    boolean existsBySavedListIdAndProductIdAndVariantIsNull(
            @Param("savedListId") java.util.UUID savedListId,
            @Param("productId") java.util.UUID productId);
}
