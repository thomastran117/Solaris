package backend.models.core;

import backend.models.enums.POStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purchase_orders", indexes = {
        @Index(name = "idx_po_company", columnList = "company_id"),
        @Index(name = "idx_po_supplier", columnList = "supplier_id"),
        @Index(name = "idx_po_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class PurchaseOrder {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private POStatus status = POStatus.DRAFT;

    @Column(nullable = true)
    private LocalDate expectedArrivalDate;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private long totalCostCents = 0;

    @Column(name = "restock_request_id", nullable = true)
    private UUID restockRequestId;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = true)
    private Instant sentAt;

    @Column(nullable = true)
    private Instant receivedAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
