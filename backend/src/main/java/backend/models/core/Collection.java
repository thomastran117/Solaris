package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;

import java.time.Instant;

/**
 * A merchant-curated grouping of products surfaced on the storefront.
 *
 * <p>Two flavours, distinguished by {@link #type}:
 * <ul>
 *   <li>{@link CollectionType#STATIC} — members are added by hand from the admin UI and
 *       persisted as {@code CollectionProduct} rows with {@code source = MANUAL}.</li>
 *   <li>{@link CollectionType#DYNAMIC} — {@link #rulesJson} encodes tag/category/brand
 *       match criteria. {@code DynamicCollectionWorker} materialises matching products into
 *       {@code CollectionProduct} rows with {@code source = AUTO} so that browse queries and
 *       per-collection pin/boost overrides share one table shape.</li>
 * </ul>
 *
 * <p>Featuring is independent of {@code type}: {@link #featured} + {@link #featuredRank}
 * control whether the collection surfaces on the marketplace home page and vendor storefront
 * sections. Only {@code status = ACTIVE} collections are eligible.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "collections", indexes = {
        @Index(name = "idx_collection_company", columnList = "company_id"),
        @Index(name = "idx_collection_company_slug", columnList = "company_id, slug", unique = true),
        @Index(name = "idx_collection_featured", columnList = "featured, featured_rank")
})
@EntityListeners(AuditingEntityListener.class)
public class Collection {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * URL-safe identifier, unique within a company. Used as the public lookup key on the
     * marketplace endpoint ({@code GET /marketplaces/{mId}/collections/{slug}}).
     */
    @Column(nullable = false, length = 200)
    private String slug;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", nullable = true, length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollectionType type = CollectionType.STATIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollectionStatus status = CollectionStatus.DRAFT;

    /** True = appears in storefront featured rails (marketplace home, vendor page). */
    @Column(nullable = false)
    private boolean featured = false;

    /** Lower = surfaced first among featured collections. Ignored when {@link #featured} is false. */
    @Column(name = "featured_rank", nullable = true)
    private Integer featuredRank;

    /**
     * JSON-encoded rule body for DYNAMIC collections. Shape (v1):
     * <pre>
     * { "tagsAnyOf": [...], "categoriesAnyOf": [...], "brandsAnyOf": [...] }
     * </pre>
     * Empty / null on STATIC collections. Parsed by {@code DynamicCollectionRules.parse}.
     */
    @Column(name = "rules_json", nullable = true, columnDefinition = "TEXT")
    private String rulesJson;

    /**
     * Marks the last successful materialisation pass for a DYNAMIC collection. Used by the
     * worker to skip collections that were just refreshed via the admin "Refresh" button.
     */
    @Column(name = "last_materialised_at", nullable = true)
    private Instant lastMaterialisedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
