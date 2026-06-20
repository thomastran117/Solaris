package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purchase_order_items", indexes = {
        @Index(name = "idx_poi_po", columnList = "purchase_order_id"),
        @Index(name = "idx_poi_product", columnList = "product_id")
})
public class PurchaseOrderItem {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "location_id", nullable = true)
    private InventoryLocation location;

    @Column(nullable = false)
    private int orderedQty;

    @Column(nullable = false)
    private int receivedQty = 0;

    @Column(nullable = false)
    private long unitCostCents;

    // NOTE: No @Version here by design. Concurrent receipts are made safe via an
    // atomic conditional UPDATE (PurchaseOrderItemRepository.incrementReceivedQty),
    // which is compatible with the persistence-context clear performed by
    // ProductRepository.adjustStock (clearAutomatically=true) during receipt.
}
