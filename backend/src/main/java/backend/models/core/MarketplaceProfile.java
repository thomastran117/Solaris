package backend.models.core;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.PayoutSchedule;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "marketplace_profiles", indexes = {
        @Index(name = "idx_marketplace_slug", columnList = "slug", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class MarketplaceProfile {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(nullable = false, length = 100, unique = true)
    private String slug;

    /** Default commission policy ID applied to all vendors unless overridden on MarketplaceVendor. */
    @Column(nullable = true, columnDefinition = "BINARY(16)")
    private java.util.UUID defaultCommissionPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutSchedule payoutSchedule = PayoutSchedule.WEEKLY;

    /** Number of days to hold vendor funds after order delivery before making available for payout. Zero = no hold. */
    @Min(0)
    @Column(nullable = false)
    private int holdPeriodDays = 7;

    @Column(nullable = false, length = 3)
    private String defaultCurrency = "USD";

    /** When false, the marketplace is closed to new vendor applications. */
    @Column(nullable = false)
    private boolean acceptingApplications = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
