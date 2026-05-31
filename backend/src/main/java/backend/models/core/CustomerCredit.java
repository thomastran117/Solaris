package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.CreditEntryType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer_credits", indexes = {
        @Index(name = "idx_credit_user", columnList = "user_id"),
        @Index(name = "idx_credit_user_created", columnList = "user_id, created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class CustomerCredit {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Positive for issuance/adjustment; negative for redemption/expiry/reversal. */
    @Column(nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private CreditEntryType type;

    @Column(nullable = true, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_id", nullable = true)
    private User issuedBy;

    /** Loose FK to support_tickets.id. */
    @Column(nullable = true, columnDefinition = "BINARY(16)")
    private UUID sourceTicketId;

    /** Loose FK to order_issues.id. */
    @Column(nullable = true, columnDefinition = "BINARY(16)")
    private UUID sourceOrderIssueId;

    /** Loose FK to orders.id — set on REDEEMED entries. */
    @Column(nullable = true, columnDefinition = "BINARY(16)")
    private UUID redeemedOnOrderId;

    @Column(nullable = true)
    private Instant expiresAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
