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
import java.time.LocalDate;

/**
 * Daily SLA metric snapshot per vendor. One row per (vendorId, date).
 * The scheduler overwrites the row for today if it already exists.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vendor_sla_metrics", indexes = {
        @Index(name = "idx_sla_metric_vendor_date", columnList = "vendor_id, metric_date")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_sla_metric_vendor_date", columnNames = {"vendor_id", "metric_date"})
})
@EntityListeners(AuditingEntityListener.class)
public class VendorSLAMetric {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    /** FK to MarketplaceVendor.id */
    @Column(nullable = false, name = "vendor_id", columnDefinition = "BINARY(16)")
    private java.util.UUID vendorId;

    @Column(nullable = false, name = "marketplace_id", columnDefinition = "BINARY(16)")
    private java.util.UUID marketplaceId;

    @Column(nullable = false, name = "metric_date")
    private LocalDate date;

    /** Total sub-orders in the evaluation window. */
    @Column(nullable = false)
    private long totalOrders = 0;

    /** Average hours from paid_at → shipped_at (approximates P50). Null if no shipped orders. */
    @Column(nullable = true)
    private Double shipHoursP50;

    /** 90th-percentile approximation of ship hours. Null if fewer than 10 shipped orders. */
    @Column(nullable = true)
    private Double shipHoursP90;

    /** Cancellation rate = cancelled / total (0.0–1.0). */
    @Column(nullable = false)
    private double cancellationRate = 0.0;

    /** Refund/return rate = returned / total (0.0–1.0). */
    @Column(nullable = false)
    private double refundRate = 0.0;

    /** Late-shipment rate = shipped_late / total_shipped (0.0–1.0). */
    @Column(nullable = false)
    private double lateShipmentRate = 0.0;

    /** Defect rate = (cancelled + returned) / total (0.0–1.0). */
    @Column(nullable = false)
    private double defectRate = 0.0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
