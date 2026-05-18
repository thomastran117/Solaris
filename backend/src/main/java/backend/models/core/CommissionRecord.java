package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "commission_records", indexes = {
        @Index(name = "idx_commission_record_sub_order", columnList = "sub_order_id", unique = true),
        @Index(name = "idx_commission_record_vendor", columnList = "vendor_id"),
        @Index(name = "idx_commission_record_marketplace", columnList = "marketplace_id")
})
@EntityListeners(AuditingEntityListener.class)
public class CommissionRecord {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_order_id", nullable = false, unique = true)
    private SubOrder subOrder;

    /** MarketplaceVendor.id — denormalized for efficient payout queries. */
    @Column(nullable = false, name = "vendor_id", columnDefinition = "BINARY(16)")
    private java.util.UUID vendorId;

    @Column(nullable = false, name = "marketplace_id", columnDefinition = "BINARY(16)")
    private java.util.UUID marketplaceId;

    /** Commission rate applied. E.g. 0.1500 = 15%. */
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal commissionRate;

    /** Gross order subtotal before commission. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    /** Marketplace take: grossAmount * commissionRate. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    /** Amount owed to the vendor: grossAmount - commissionAmount. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netVendorAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * True once the scheduler has moved this record's netVendorAmount from
     * VendorBalance.pendingCents to availableCents after the hold period expires.
     */
    @Column(nullable = false)
    private boolean holdReleased = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant computedAt;
}
