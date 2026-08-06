package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A single negotiated line on a {@link B2BQuote} (Feature 12). Prices are stored in cents; the
 * unit price is what the order item is created with, bypassing the promotion engine.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "b2b_quote_items", indexes = {
        @Index(name = "idx_b2b_quote_item_quote", columnList = "quote_id"),
        @Index(name = "idx_b2b_quote_item_product", columnList = "product_id")
})
public class B2BQuoteItem {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private B2BQuote quote;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id", nullable = true)
    private UUID variantId;

    @Column(nullable = true, length = 255)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long unitPriceCents;

    @Column(nullable = false)
    private long totalPriceCents;

    // NOTE: No @Version / timestamps by design (mirrors OrderItem). Quote lines are immutable once
    // written: a vendor counter-offer replaces the whole collection via orphanRemoval rather than
    // mutating existing rows, so there is no in-place concurrent update to guard with optimistic
    // locking. The parent B2BQuote carries @Version for the lifecycle transitions.

    /** Recomputes {@code totalPriceCents} from unit price and quantity. */
    public void recomputeTotal() {
        this.totalPriceCents = this.unitPriceCents * this.quantity;
    }
}
