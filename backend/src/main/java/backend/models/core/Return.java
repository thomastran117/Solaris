package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.RefundStatus;
import backend.models.enums.ReturnReason;
import backend.models.enums.ReturnStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "returns", indexes = {
        @Index(name = "idx_return_order", columnList = "order_id"),
        @Index(name = "idx_return_stripe_refund", columnList = "stripe_refund_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_return_stripe_refund_id", columnNames = "stripe_refund_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Return {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    /**
     * Optimistic-lock guard. Round 1 added {@code @Version} to Order/SubOrder/Coupon
     * but missed Return. Two concurrent {@code approveReturn} calls both load a
     * REQUESTED return, both pass the state-check, both issue a Stripe refund — and
     * without this column there is nothing to fail the second writer on.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Null for merchant-initiated returns. Non-null for buyer-initiated requests. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturnItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 30)
    private ReturnReason reason;

    /** Buyer-supplied description of the return reason. */
    @Column(nullable = true, length = 1000)
    private String buyerNote;

    /** S3 public URLs for buyer-supplied evidence images submitted with the return request. */
    @ElementCollection
    @CollectionTable(name = "return_evidence_urls", joinColumns = @JoinColumn(name = "return_id"))
    @Column(name = "url", nullable = false, length = 500)
    private List<String> evidenceUrls = new ArrayList<>();

    /**
     * Snapshot of the merchant's return shipping address captured at approval time.
     * Null until approveReturn() or merchantInitiateReturn() is called. Stored as a snapshot
     * so future changes to the company's return locations don't affect in-flight returns.
     */
    @Column(nullable = true, length = 255) private String returnShipToAddress;
    @Column(nullable = true, length = 100) private String returnShipToCity;
    @Column(nullable = true, length = 100) private String returnShipToCountry;
    @Column(nullable = true, length = 20)  private String returnShipToPostalCode;

    /** Merchant note recorded at approval or rejection time. */
    @Column(nullable = true, length = 500)
    private String merchantNote;

    /** Whether the merchant elected to restock returned items. */
    @Column(nullable = false)
    private boolean restockItems = false;

    // -------------------------------------------------------------------------
    // Refund tracking
    // -------------------------------------------------------------------------

    /** Refund amount in cents. Null until a refund is attempted. Zero means intentionally waived. */
    @Column(nullable = true)
    private Long refundedAmountCents;

    /** Stripe refund ID (re_...). Used to correlate charge.refunded / refund.updated webhook events. */
    @Column(nullable = true, length = 255)
    private String stripeRefundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Column(nullable = true, length = 500)
    private String refundFailureReason;

    // -------------------------------------------------------------------------
    // Timestamps
    // -------------------------------------------------------------------------

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = true)
    private Instant approvedAt;

    @Column(nullable = true)
    private Instant completedAt;

    /** Loose FK to sub_orders.id — set for returns on marketplace vendor sub-orders. */
    @Column(nullable = true)
    private Long subOrderId;
}
