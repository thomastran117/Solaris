package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.CollectionMembershipSource;

import java.time.Instant;

/**
 * Join row between {@link Collection} and {@link Product}.
 *
 * <p>Carries optional per-collection overrides for pin and boost so that a product can rank
 * differently inside a curated collection than it does in global search. When both per-collection
 * and global values are present, the per-collection value wins inside that collection's listing.
 *
 * <p>{@link #source} identifies whether the row was inserted manually (admin clicked "add product")
 * or by {@code DynamicCollectionWorker}. The worker only touches rows with {@code source = AUTO};
 * MANUAL rows on a DYNAMIC collection are unusual but allowed (pin a hand-picked extra into a
 * mostly-auto collection) and are preserved across rule refreshes.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "collection_products", indexes = {
        @Index(name = "idx_cp_collection", columnList = "collection_id"),
        @Index(name = "idx_cp_product", columnList = "product_id"),
        @Index(name = "idx_cp_collection_product", columnList = "collection_id, product_id", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class CollectionProduct {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Per-collection boost multiplier (1–10). Overrides {@code product.boostWeight} inside this collection. */
    @Column(name = "boost_weight", nullable = true)
    private Integer boostWeight;

    /** Per-collection pin order. When set, the product surfaces at the top of the collection page. */
    @Column(name = "pinned_rank", nullable = true)
    private Integer pinnedRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CollectionMembershipSource source = CollectionMembershipSource.MANUAL;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
