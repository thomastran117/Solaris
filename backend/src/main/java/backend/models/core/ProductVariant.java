package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_variants", indexes = {
        @Index(name = "idx_variant_product", columnList = "product_id"),
        @Index(name = "idx_variant_sku", columnList = "sku"),
        // Enforces company-scoped SKU uniqueness at the DB so a concurrent create cannot slip a
        // duplicate past the service-level existsBy check. NULL skus are exempt (a unique index
        // treats NULLs as distinct), so variants without a SKU are unaffected. Note the index is
        // case-sensitive on PostgreSQL; the service check uses an IgnoreCase query, so it rejects
        // case-variant duplicates before they reach this constraint.
        @Index(name = "uq_variant_company_sku", columnList = "company_id, sku", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Denormalized owning-company id, kept in sync from {@link #product} by {@link #syncCompanyId()}.
     * Exists solely to back the {@code (company_id, sku)} unique index — variants are always scoped
     * to their product's company.
     */
    @Column(name = "company_id", nullable = true)
    private java.util.UUID companyId;

    @Column(nullable = true, length = 100)
    private String sku;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = true, precision = 12, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(nullable = true)
    private Integer stock;

    @Column(nullable = true)
    private Integer lowStockThreshold;

    /** Alert when stock falls to this percentage of maxStock (0–100). Null = no percent threshold. */
    @Column(nullable = true)
    private Integer lowStockThresholdPercent;

    /** Maximum / initial stock capacity. Used as denominator for percent threshold calculation. */
    @Column(nullable = true)
    private Integer maxStock;

    /** When true, a PENDING RestockRequest is automatically created when stock breaches a threshold. */
    @Column(nullable = false)
    private boolean autoRestockEnabled = false;

    /** Units to request in the auto-generated RestockRequest. Required when autoRestockEnabled is true. */
    @Column(nullable = true)
    private Integer autoRestockQty;

    @Column(nullable = false)
    private boolean purchasable = true;

    @Column(nullable = false)
    private boolean backorderEnabled = false;

    @Column(nullable = false)
    private boolean preorderEnabled = false;

    @Column(nullable = true)
    private Instant preorderExpectedDate;

    @Column(nullable = true, length = 100)
    private String option1;

    @Column(nullable = true, length = 100)
    private String option2;

    @Column(nullable = true, length = 100)
    private String option3;

    @Column(nullable = false)
    private int displayOrder = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = true, updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = true)
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Keeps the denormalized {@link #companyId} consistent with the owning product on every
     * persist/update. {@code product.getCompany().getId()} reads the FK from the lazy proxy
     * without forcing a full load.
     */
    @PrePersist
    @PreUpdate
    void syncCompanyId() {
        if (product != null && product.getCompany() != null) {
            this.companyId = product.getCompany().getId();
        }
    }
}
