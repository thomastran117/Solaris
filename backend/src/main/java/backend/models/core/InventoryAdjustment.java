package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.AdjustmentReason;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventory_adjustments", indexes = {
        @Index(name = "idx_inv_adj_product", columnList = "product_id"),
        @Index(name = "idx_inv_adj_product_created", columnList = "product_id, created_at"),
        @Index(name = "idx_inv_adj_order", columnList = "order_id")
})
@EntityListeners(AuditingEntityListener.class)
public class InventoryAdjustment {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    private User adjustedBy;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private int previousStock;

    @Column(nullable = false)
    private int newStock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdjustmentReason reason;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String note;

    @Column(name = "order_id", nullable = true)
    private UUID orderId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
