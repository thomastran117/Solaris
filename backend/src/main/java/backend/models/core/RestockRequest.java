package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.RestockStatus;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "restock_requests", indexes = {
        @Index(name = "idx_restock_product", columnList = "product_id"),
        @Index(name = "idx_restock_company", columnList = "company_id"),
        @Index(name = "idx_restock_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class RestockRequest {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    /** Denormalized company FK for efficient company-scoped queries without a JOIN. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    /** Target warehouse to receive stock into. Null means unassigned (global stock only). */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "location_id", nullable = true)
    private InventoryLocation location;

    @Column(nullable = false)
    private int requestedQty;

    /** Filled when status transitions to RECEIVED. */
    @Column(nullable = true)
    private Integer receivedQty;

    @Column(nullable = true)
    private LocalDate expectedArrivalDate;

    /** Set when status transitions to RECEIVED. Used by the SLA dashboard for supplier-lateness. */
    @Column(nullable = true)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestockStatus status = RestockStatus.PENDING;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String supplierNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
