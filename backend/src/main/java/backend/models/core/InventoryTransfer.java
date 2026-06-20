package backend.models.core;

import backend.models.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A stock movement of a single product between two of a company's {@link InventoryLocation}s.
 *
 * <p>v1 is product-level only — variant-managed products are rejected at create, so the stock
 * legs always operate on the product-level {@link LocationStock} row (variantRef = null).
 *
 * <p>Stock is not reserved: it moves atomically only on receipt. See
 * {@code InventoryTransferServiceImpl} for the lifecycle and concurrency guarantees. The
 * {@link #version} column backs the once-only-receipt guard under concurrent receive requests.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "inventory_transfers", indexes = {
        @Index(name = "idx_transfer_company", columnList = "company_id"),
        @Index(name = "idx_transfer_company_status", columnList = "company_id, status")
})
@EntityListeners(AuditingEntityListener.class)
public class InventoryTransfer {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_location_id", nullable = false)
    private InventoryLocation fromLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_location_id", nullable = false)
    private InventoryLocation toLocation;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.PENDING;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "received_by", nullable = true)
    private User receivedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cancelled_by", nullable = true)
    private User cancelledBy;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = true)
    private Instant inTransitAt;

    @Column(nullable = true)
    private Instant receivedAt;

    @Column(nullable = true)
    private Instant cancelledAt;
}
