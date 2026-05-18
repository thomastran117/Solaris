package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.PayoutStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_payouts", indexes = {
        @Index(name = "idx_vendor_payout_vendor", columnList = "vendor_id"),
        @Index(name = "idx_vendor_payout_status", columnList = "vendor_id, status"),
        @Index(name = "idx_vendor_payout_stripe_transfer", columnList = "stripe_transfer_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_vendor_payout_stripe_transfer_id", columnNames = "stripe_transfer_id")
})
@EntityListeners(AuditingEntityListener.class)
public class VendorPayout {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    /** MarketplaceVendor.id — the vendor receiving this payout. */
    @Column(nullable = false, name = "vendor_id", columnDefinition = "BINARY(16)")
    private java.util.UUID vendorId;

    @Column(nullable = false, name = "marketplace_id", columnDefinition = "BINARY(16)")
    private java.util.UUID marketplaceId;

    @Column(nullable = true)
    private Instant periodStart;

    @Column(nullable = true)
    private Instant periodEnd;

    /** Sum of all sub-order totals included in this payout. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    /** Total commission deducted from grossAmount. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    /** Total refunds deducted from grossAmount. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    /** Manual adjustments (positive = credit, negative = debit). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal adjustmentAmount = BigDecimal.ZERO;

    /** netAmount = grossAmount - commissionAmount - refundAmount + adjustmentAmount */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.SCHEDULED;

    /** Stripe transfer ID (tr_...) — set when transfer is created. */
    @Column(nullable = true, length = 255, name = "stripe_transfer_id")
    private String stripeTransferId;

    /** Stripe payout ID (po_...) — set after payout reaches the vendor's bank. */
    @Column(nullable = true, length = 255)
    private String stripePayoutId;

    @Column(nullable = true, length = 500)
    private String failureReason;

    @Column(nullable = true)
    private Instant scheduledAt;

    @Column(nullable = true)
    private Instant paidAt;

    @OneToMany(mappedBy = "payout", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VendorPayoutItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
