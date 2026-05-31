package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.SLABreachAction;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_sla_breaches", indexes = {
        @Index(name = "idx_sla_breach_vendor", columnList = "vendor_id"),
        @Index(name = "idx_sla_breach_detected", columnList = "vendor_id, detected_at"),
        @Index(name = "idx_sla_breach_unresolved", columnList = "vendor_id, resolved_at")
})
@EntityListeners(AuditingEntityListener.class)
public class VendorSLABreach {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    /** FK to MarketplaceVendor.id */
    @Column(nullable = false, name = "vendor_id", columnDefinition = "BINARY(16)")
    private java.util.UUID vendorId;

    /** FK to VendorSLAPolicy.id */
    @Column(nullable = false, name = "policy_id", columnDefinition = "BINARY(16)")
    private java.util.UUID policyId;

    /** Which metric threshold was violated (e.g. "cancellationRate", "lateShipmentRate"). */
    @Column(nullable = false, length = 60)
    private String metric;

    @Column(nullable = false)
    private double actualValue;

    @Column(nullable = false)
    private double threshold;

    @Column(nullable = false, name = "detected_at")
    private Instant detectedAt;

    @Column(nullable = true, name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "action_taken")
    private SLABreachAction actionTaken;

    @Column(nullable = true, name = "notification_sent_at")
    private Instant notificationSentAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
