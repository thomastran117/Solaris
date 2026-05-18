package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_stocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_loc_stock",
                columnNames = {"location_id", "product_id", "variant_ref"}
        ),
        indexes = {
                @Index(name = "idx_loc_stock_location", columnList = "location_id"),
                @Index(name = "idx_loc_stock_product",  columnList = "product_id"),
                @Index(name = "idx_loc_stock_variant",  columnList = "variant_ref")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class LocationStock {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Optional variant navigation — read-only from JPA perspective.
     * The actual FK column (variant_ref) is managed by the {@link #variantRef} field.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_ref", insertable = false, updatable = false)
    private ProductVariant variant;

    /** Variant discriminator for variant-level inventory. Null means product-level stock. */
    @Column(name = "variant_ref", columnDefinition = "BINARY(16)")
    private UUID variantRef;

    @Column(nullable = false)
    private int stock = 0;

    @Column(nullable = true)
    private Integer lowStockThreshold;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public boolean isProductLevel() {
        return variantRef == null;
    }
}
