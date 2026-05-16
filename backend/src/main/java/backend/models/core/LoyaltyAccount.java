package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "loyalty_accounts", indexes = {
        @Index(name = "idx_loyalty_account_user", columnList = "user_id"),
        @Index(name = "idx_loyalty_account_company", columnList = "company_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_loyalty_account_user_company", columnNames = {"user_id", "company_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class LoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "user_id")
    private Long userId;

    @Column(nullable = false, name = "company_id")
    private Long companyId;

    /** Current redeemable points balance. Never negative. */
    @Column(nullable = false)
    private long pointsBalance = 0L;

    /** Total lifetime points earned (used for tier evaluation). Never decremented on redemption. */
    @Column(nullable = false)
    private long lifetimePoints = 0L;

    /** Loose FK to LoyaltyTier.id. Null until the account earns enough for a tier. */
    @Column(nullable = true, name = "current_tier_id")
    private Long currentTierId;

    @Column(nullable = true)
    private Instant tierUpdatedAt;

    /** Calendar year in which the last birthday reward was issued; null if never issued. Used for atomic idempotency. */
    @Column(nullable = true, name = "last_birthday_reward_year")
    private Integer lastBirthdayRewardYear;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
