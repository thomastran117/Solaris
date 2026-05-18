package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_balances")
@EntityListeners(AuditingEntityListener.class)
public class VendorBalance {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    /** MarketplaceVendor.id — unique, one balance row per vendor. */
    @Column(nullable = false, unique = true, name = "vendor_id", columnDefinition = "BINARY(16)")
    private java.util.UUID vendorId;

    /** Funds within the hold period — not yet available for payout (in smallest currency unit). */
    @Column(nullable = false)
    private long pendingCents = 0L;

    /** Funds past the hold period, eligible for payout disbursement. */
    @Column(nullable = false)
    private long availableCents = 0L;

    /** Funds currently in a PROCESSING payout — awaiting Stripe transfer confirmation. */
    @Column(nullable = false)
    private long inTransitCents = 0L;

    /** Running total of all gross sales credited to this vendor. */
    @Column(nullable = false)
    private long lifetimeGrossCents = 0L;

    /** Running total of all commission deducted from this vendor. */
    @Column(nullable = false)
    private long lifetimeCommissionCents = 0L;

    /** Running total of all net amounts that have been paid out (transfer.paid confirmed). */
    @Column(nullable = false)
    private long lifetimePaidOutCents = 0L;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
