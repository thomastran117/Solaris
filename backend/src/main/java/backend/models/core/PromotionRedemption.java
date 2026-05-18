package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Audit row written once per applied rule per order. Snapshots the saving amount and the
 * funding company at redemption time for downstream settlement/reporting.
 */
@Entity
@Table(name = "promotion_redemptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_promo_redemption_rule_order",
                columnNames = {"rule_id", "order_id"}),
        indexes = {
                @Index(name = "idx_promo_redemption_rule",   columnList = "rule_id"),
                @Index(name = "idx_promo_redemption_order",  columnList = "order_id"),
                @Index(name = "idx_promo_redemption_user",   columnList = "user_id"),
                @Index(name = "idx_promo_redemption_funder", columnList = "funded_by_company_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class PromotionRedemption {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private PromotionRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The actual savings this rule contributed to this order. Snapshotted. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    /**
     * Snapshot of {@code rule.fundedByCompany} (or {@code rule.company} when that is null).
     * Captured at redemption so later edits to the rule do not alter historical settlement.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funded_by_company_id", nullable = false)
    private Company fundedByCompany;

    @Column(nullable = false)
    private Instant redeemedAt;
}
