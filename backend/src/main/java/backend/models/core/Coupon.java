package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.DiscountStatus;
import backend.models.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "coupons", indexes = {
        @Index(name = "idx_coupon_company",  columnList = "company_id"),
        @Index(name = "idx_coupon_status",   columnList = "status"),
        @Index(name = "idx_coupon_end_date", columnList = "end_date")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-lock guard. Used together with the atomic redemption SQL in
     * {@link backend.repositories.CouponRepository} so the redemption-cap check
     * cannot be bypassed under concurrent checkouts.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Backing PromotionRule auto-created/maintained by CouponService. Lets the pricing
     * engine apply coupon-gated discounts the same way it applies any other rule.
     * Null during a brief window between coupon creation and rule sync; also null for
     * coupons created before the backing-rule sync was introduced.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "promotion_rule_id", nullable = true)
    private PromotionRule rule;

    /**
     * Globally unique redemption code (stored uppercase).
     * Immutable after creation — distributed codes cannot be changed.
     */
    @Column(nullable = false, length = 50, unique = true)
    private String code;

    /** Human-readable label (e.g. "Summer 2024 Sale"). */
    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    /** Percentage (0 < value ≤ 100) for PERCENTAGE; absolute amount for FIXED_AMOUNT. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    /**
     * Stored status: ACTIVE or DISABLED only.
     * EXPIRED is computed at response time when endDate is non-null and in the past.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountStatus status = DiscountStatus.ACTIVE;

    /** Null = immediately effective. */
    @Column(nullable = true)
    private Instant startDate;

    /** Null = never expires. */
    @Column(nullable = true)
    private Instant endDate;

    /** Total redemption cap across all users. Null = unlimited. */
    @Column(nullable = true)
    private Integer maxUses;

    /** Running total of successful redemptions. Incremented atomically at checkout. */
    @Column(nullable = false)
    private int usedCount = 0;

    /** Per-user redemption cap. Null = unlimited. */
    @Column(nullable = true)
    private Integer maxUsesPerUser;

    /** Minimum pre-coupon order total required to apply this coupon. Null = no minimum. */
    @Column(nullable = true, precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
