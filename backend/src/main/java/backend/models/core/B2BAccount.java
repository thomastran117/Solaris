package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Business buyer profile (Feature 12). Auto-created/linked the first time a user requests a B2B
 * quote. {@code netTermsApproved} gates whether the buyer may accept NET_30/NET_60 terms;
 * {@code netTermsLimitCents} caps their outstanding (unpaid) invoice balance.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "b2b_accounts", indexes = {
        @Index(name = "idx_b2b_account_user", columnList = "user_id", unique = true)
})
public class B2BAccount {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String companyName;

    @Column(nullable = true, length = 100)
    private String taxId;

    @Column(nullable = true, length = 500)
    private String billingAddress;

    @Column(nullable = false)
    private boolean netTermsApproved = false;

    /** Maximum outstanding (ISSUED/OVERDUE) invoice balance allowed, in cents. */
    @Column(nullable = false)
    private long netTermsLimitCents = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
