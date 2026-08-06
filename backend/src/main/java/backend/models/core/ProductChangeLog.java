package backend.models.core;

import backend.models.enums.ChangeSource;
import backend.models.enums.ProductChangeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Per-field audit row for {@link Product} / {@link ProductVariant} edits.
 *
 * <p>Stock-related fields are intentionally <b>not</b> recorded here — they live in
 * {@link InventoryAdjustment} with delta / previous / new stock semantics. The history
 * endpoint merges both tables into a single timeline.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "product_change_log", indexes = {
        @Index(name = "idx_pcl_product_changed", columnList = "product_id, changed_at"),
        @Index(name = "idx_pcl_variant_changed", columnList = "variant_id, changed_at"),
        @Index(name = "idx_pcl_company_changed", columnList = "company_id, changed_at"),
        @Index(name = "idx_pcl_changed_by", columnList = "changed_by")
})
@EntityListeners(AuditingEntityListener.class)
public class ProductChangeLog {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    @Column(name = "company_id", nullable = false)
    private java.util.UUID companyId;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "old_value", nullable = true, columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", nullable = true, columnDefinition = "TEXT")
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ProductChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeSource source;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "changed_by", nullable = true)
    private User changedBy;

    @Column(name = "reverted_from_log_id", nullable = true)
    private java.util.UUID revertedFromLogId;

    @CreatedDate
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
