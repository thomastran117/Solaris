package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.SubOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sub_orders", indexes = {
        @Index(name = "idx_sub_order_order", columnList = "order_id"),
        @Index(name = "idx_sub_order_vendor", columnList = "marketplace_vendor_id"),
        @Index(name = "idx_sub_order_status", columnList = "status"),
        @Index(name = "idx_sub_order_marketplace", columnList = "marketplace_id")
})
@EntityListeners(AuditingEntityListener.class)
public class SubOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-lock guard. Vendor and marketplace operators can concurrently approve,
     * cancel, or settle a sub-order; the @Version makes those concurrent writes fail
     * loudly instead of silently dropping commission/status updates.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marketplace_vendor_id", nullable = false)
    private MarketplaceVendor marketplaceVendor;

    /** Denormalized from marketplaceVendor.marketplace.id for efficient filtering. */
    @Column(nullable = false, name = "marketplace_id")
    private Long marketplaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubOrderStatus status = SubOrderStatus.PENDING;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vendorDiscountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    /** Snapshot of commission amount once CommissionRecord is computed. Null until payment confirmed. */
    @Column(nullable = true, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    /** Snapshot of net vendor payout once CommissionRecord is computed. */
    @Column(nullable = true, precision = 12, scale = 2)
    private BigDecimal netVendorAmount;

    // -------------------------------------------------------------------------
    // SLA timestamps
    // -------------------------------------------------------------------------

    @Column(nullable = true)
    private Instant paidAt;

    @Column(nullable = true)
    private Instant packedAt;

    @Column(nullable = true)
    private Instant shippedAt;

    @Column(nullable = true)
    private Instant deliveredAt;

    @Column(nullable = true)
    private Instant cancelledAt;

    @Column(nullable = true)
    private Instant returnedAt;

    // -------------------------------------------------------------------------
    // Fulfillment
    // -------------------------------------------------------------------------

    @Column(nullable = true, length = 100)
    private String trackingNumber;

    @Column(nullable = true, length = 60)
    private String carrier;

    @Column(nullable = true, length = 500)
    private String fulfillmentNote;

    @Column(nullable = true, length = 50)
    private String cancellationReason;

    /** Loose FK to VendorPayout.id — set when this sub-order is included in a payout batch (Phase 4). */
    @Column(nullable = true)
    private Long payoutId;

    // -------------------------------------------------------------------------
    // Audit
    // -------------------------------------------------------------------------

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
