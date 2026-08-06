package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_kit_selections", indexes = {
        @Index(name = "idx_oks_order_item", columnList = "order_item_id")
})
@Getter
@Setter
@NoArgsConstructor
public class OrderKitSelection {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /** Nullable so the selection survives kit/slot deletion. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "slot_id", nullable = true)
    private KitSlot slot;

    @Column(nullable = false, length = 255)
    private String slotName;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    @Column(nullable = false, length = 255)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    @Column(nullable = true, length = 255)
    private String variantTitle;

    @Column(nullable = true, length = 100)
    private String variantSku;

    @Column(nullable = false)
    private int quantity;

    /** Snapshot of (variant/product price + priceDelta) at order time. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
