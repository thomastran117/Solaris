package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_adjustments", indexes = {
        @Index(name = "idx_vadj_vendor", columnList = "vendor_id")
})
@EntityListeners(AuditingEntityListener.class)
public class VendorAdjustment {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    /** MarketplaceVendor.id. */
    @Column(nullable = false, name = "vendor_id")
    private UUID vendorId;

    /** Signed amount in smallest currency unit (positive = credit, negative = debit). */
    @Column(nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, name = "created_by_user_id")
    private UUID createdByUserId;

    /** Loose FK to vendor_payouts.id — set when this adjustment is included in a payout batch. */
    @Column(nullable = true, name = "applied_to_payout_id")
    private Long appliedToPayoutId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
