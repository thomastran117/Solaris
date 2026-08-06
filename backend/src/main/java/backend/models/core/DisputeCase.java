package backend.models.core;

import backend.models.enums.DisputeOutcome;
import backend.models.enums.DisputeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * An internal chargeback case, opened when Stripe delivers {@code charge.dispute.created}.
 *
 * <p>{@link #stripeDisputeId} is unique: it is the idempotency key for the whole feature. Stripe
 * delivers at-least-once and may re-issue the same dispute under a fresh event id, so the
 * constraint — not the controller's Redis event-id guard — is what guarantees one case per dispute.
 *
 * <p>{@link #order} is nullable on purpose. A dispute can arrive for a charge whose payment intent
 * no longer resolves to an order (manual charge, data migration, deleted order); dropping the case
 * in that situation would silently lose the deadline, so the case is stored either way and the
 * missing linkage is logged for a human.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "dispute_cases", indexes = {
        @Index(name = "idx_dispute_order", columnList = "order_id"),
        @Index(name = "idx_dispute_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_dispute_stripe_id", columnNames = "stripe_dispute_id")
})
public class DisputeCase {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private UUID id;

    /** The disputed order. Null when the charge could not be mapped back to one. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "order_id", nullable = true)
    private Order order;

    @Column(name = "stripe_dispute_id", nullable = false, length = 100)
    private String stripeDisputeId;

    @Column(name = "stripe_charge_id", nullable = true, length = 100)
    private String stripeChargeId;

    @Column(name = "stripe_payment_intent_id", nullable = true, length = 255)
    private String stripePaymentIntentId;

    /** Disputed amount in the smallest currency unit. */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency = "usd";

    /** Raw Stripe reason code (e.g. {@code fraudulent}, {@code product_not_received}). */
    @Column(nullable = true, length = 60)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisputeOutcome outcome = DisputeOutcome.PENDING;

    /**
     * Raw Stripe status kept verbatim. The three-state {@link DisputeStatus} loses the
     * inquiry-vs-dispute distinction, which support needs when triaging.
     */
    @Column(name = "stripe_status", nullable = true, length = 40)
    private String stripeStatus;

    /** When evidence must be submitted by. Null when Stripe did not supply a deadline. */
    @Column(name = "evidence_deadline", nullable = true)
    private Instant evidenceDeadline;

    @Column(name = "closed_at", nullable = true)
    private Instant closedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
