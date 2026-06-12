package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.OnboardingStep;
import backend.models.enums.StripeConnectStatus;
import backend.models.enums.VendorStatus;
import backend.models.enums.VendorTier;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "marketplace_vendors", indexes = {
        @Index(name = "idx_mv_marketplace", columnList = "marketplace_id"),
        @Index(name = "idx_mv_vendor_company", columnList = "vendor_company_id"),
        @Index(name = "idx_mv_status", columnList = "status"),
        @Index(name = "idx_mv_stripe_account", columnList = "stripe_connect_account_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_mv_marketplace_vendor", columnNames = {"marketplace_id", "vendor_company_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class MarketplaceVendor {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marketplace_id", nullable = false)
    private Company marketplace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_company_id", nullable = false)
    private Company vendorCompany;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorStatus status = VendorStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorTier tier = VendorTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnboardingStep onboardingStep = OnboardingStep.PROFILE;

    /** Override commission policy for this vendor; null means use marketplace default. */
    @Column(nullable = true, columnDefinition = "BINARY(16)")
    private java.util.UUID commissionPolicyId;

    @Column(nullable = true, length = 255)
    private String stripeConnectAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    private StripeConnectStatus stripeConnectStatus;

    @Column(nullable = false)
    private boolean chargesEnabled = false;

    @Column(nullable = false)
    private boolean payoutsEnabled = false;

    @Column(nullable = true)
    private Instant appliedAt;

    @Column(nullable = true)
    private Instant approvedAt;

    @Column(nullable = true)
    private Instant suspendedAt;

    @Column(nullable = true, length = 1000)
    private String rejectionReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
