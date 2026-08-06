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

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "loyalty_accounts", indexes = {
        @Index(name = "idx_loyalty_account_user", columnList = "user_id"),
        @Index(name = "idx_loyalty_account_company", columnList = "company_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_loyalty_account_user_company", columnNames = {"user_id", "company_id"}),
        @UniqueConstraint(name = "uq_loyalty_referral_code_company", columnNames = {"referral_code", "company_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class LoyaltyAccount {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, name = "user_id")
    private UUID userId;

    @Column(nullable = false, name = "company_id")
    private UUID companyId;

    /** Current redeemable points balance. Never negative. */
    @Column(nullable = false)
    private long pointsBalance = 0L;

    /** Total lifetime points earned (used for tier evaluation). Never decremented on redemption. */
    @Column(nullable = false)
    private long lifetimePoints = 0L;

    /** Loose FK to LoyaltyTier.id. Null until the account earns enough for a tier. */
    @Column(nullable = true, name = "current_tier_id")
    private UUID currentTierId;

    @Column(nullable = true)
    private Instant tierUpdatedAt;

    /** Calendar year in which the last birthday reward was issued; null if never issued. Used for atomic idempotency. */
    @Column(nullable = true, name = "last_birthday_reward_year")
    private Integer lastBirthdayRewardYear;

    /** Unique referral code for this account within the company. Generated lazily on first request. */
    @Column(nullable = true, length = 12, name = "referral_code")
    private String referralCode;

    /** The referral code that was applied when this user joined; null if not referred. */
    @Column(nullable = true, length = 12, name = "referred_by_code")
    private String referredByCode;

    /** True once the referrer has been awarded their bonus for this account's first order. */
    @Column(nullable = false, name = "referral_converted")
    private boolean referralConverted = false;

    /** Last order year-month in "yyyy-MM" format; used for consecutive-month streak tracking. */
    @Column(nullable = true, length = 7, name = "last_order_year_month")
    private String lastOrderYearMonth;

    /** Number of consecutive months in which the account has placed at least one order. */
    @Column(nullable = false, name = "current_streak")
    private int currentStreak = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
