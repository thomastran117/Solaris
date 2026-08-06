package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.SLABreachAction;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_sla_policies", indexes = {
        @Index(name = "idx_sla_policy_marketplace", columnList = "marketplace_id"),
        @Index(name = "idx_sla_policy_active", columnList = "marketplace_id, active")
})
@EntityListeners(AuditingEntityListener.class)
public class VendorSLAPolicy {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @Column(nullable = false, name = "marketplace_id")
    private java.util.UUID marketplaceId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Target hours from order paid to shipped. */
    @Column(nullable = false)
    private double targetShipHours = 48.0;

    /** Target hours to first respond to a customer message. */
    @Column(nullable = false)
    private double targetResponseHours = 24.0;

    /** Max allowed cancellation rate (e.g. 0.02 = 2%). */
    @Column(nullable = false)
    private double maxCancellationRate = 0.02;

    /** Max allowed refund/return rate (e.g. 0.05 = 5%). */
    @Column(nullable = false)
    private double maxRefundRate = 0.05;

    /** Max allowed late-shipment rate (e.g. 0.10 = 10%). */
    @Column(nullable = false)
    private double maxLateShipmentRate = 0.10;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SLABreachAction breachAction = SLABreachAction.WARN;

    /** Rolling window in days used when evaluating vendor metrics. */
    @Column(nullable = false)
    private int evaluationWindowDays = 30;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
