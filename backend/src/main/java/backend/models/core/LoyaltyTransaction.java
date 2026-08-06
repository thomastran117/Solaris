package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.LoyaltyTransactionType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "loyalty_transactions", indexes = {
        @Index(name = "idx_loyalty_tx_account", columnList = "account_id"),
        @Index(name = "idx_loyalty_tx_user_company", columnList = "user_id, company_id"),
        @Index(name = "idx_loyalty_tx_expires", columnList = "expires_at"),
        @Index(name = "idx_loyalty_tx_order", columnList = "source_order_id")
})
@EntityListeners(AuditingEntityListener.class)
public class LoyaltyTransaction {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private LoyaltyAccount account;

    /** Denormalized from account.userId for efficient user-scoped queries. */
    @Column(nullable = false, name = "user_id")
    private UUID userId;

    /** Denormalized from account.companyId. */
    @Column(nullable = false, name = "company_id")
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private LoyaltyTransactionType type;

    /** Positive = points earned; negative = points redeemed/expired. */
    @Column(nullable = false, name = "points_delta")
    private long pointsDelta;

    /** Monetary equivalent in cents. Informational only. */
    @Column(nullable = false, name = "value_cents")
    private long valueCents = 0L;

    /** Loose FK to orders.id. Set on EARN_ORDER, REDEEM_ORDER, and CONVERT_TO_CREDIT entries. */
    @Column(nullable = true, name = "source_order_id")
    private UUID sourceOrderId;

    /** When this earn entry's points expire. Null if the policy has no expiry or for non-earn entries. */
    @Column(nullable = true, name = "expires_at")
    private Instant expiresAt;

    /** True once this earn row has been claimed by an expiry run. Prevents re-expiry on future scheduler runs. */
    @Column(nullable = false)
    private boolean expired = false;

    @Column(nullable = true, length = 500)
    private String reason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
