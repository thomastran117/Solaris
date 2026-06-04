package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.LoyaltyEarnMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "loyalty_policies", indexes = {
        @Index(name = "idx_loyalty_policy_company", columnList = "company_id"),
        @Index(name = "idx_loyalty_policy_active", columnList = "company_id, active")
})
@EntityListeners(AuditingEntityListener.class)
public class LoyaltyPolicy {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, name = "company_id", columnDefinition = "BINARY(16)")
    private UUID companyId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Points earned per dollar spent. e.g. 1.00 = 1 pt per $1. */
    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal earnRatePerDollar = BigDecimal.ONE;

    /** Cents value of one point. e.g. 1 = 1 cent → 100 pts = $1. */
    @Column(nullable = false)
    private int pointValueCents = 1;

    /** Minimum points required to redeem in a single transaction. */
    @Column(nullable = false)
    private int minRedemptionPoints = 100;

    /** Days until earned points expire. Null = points never expire. */
    @Column(nullable = true)
    private Integer pointsExpiryDays;

    /** Points awarded on the customer's birthday. 0 = disabled. */
    @Column(nullable = false)
    private int birthdayBonusPoints = 0;

    /** Store credit (cents) awarded on birthday. 0 = disabled. Applied alongside birthdayBonusPoints. */
    @Column(nullable = false)
    private int birthdayBonusCreditCents = 0;

    /** Cashback percentage of order total issued as store credit. e.g. 2.00 = 2%. 0 = disabled. */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal cashbackRatePercent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LoyaltyEarnMode earnMode = LoyaltyEarnMode.POINTS;

    @Column(nullable = false)
    private boolean active = true;

    /** Points awarded to the referrer when a referred user completes their first order. 0 = disabled. */
    @Column(nullable = false, name = "referral_bonus_points")
    private int referralBonusPoints = 0;

    /** Number of consecutive purchase months required to trigger a streak bonus. */
    @Column(nullable = false, name = "streak_bonus_threshold")
    private int streakBonusThreshold = 3;

    /** Bonus points awarded each time the streak reaches a multiple of streakBonusThreshold. 0 = disabled. */
    @Column(nullable = false, name = "streak_bonus_points")
    private int streakBonusPoints = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
