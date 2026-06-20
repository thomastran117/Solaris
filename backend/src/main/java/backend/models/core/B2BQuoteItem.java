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
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private B2BQuote quote;

    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    @Column(name = "variant_id", nullable = true, columnDefinition = "BINARY(16)")
    private UUID variantId;

    @Column(nullable = true, length = 255)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long unitPriceCents;

    @Column(nullable = false)
    private long totalPriceCents;

    /** Recomputes {@code totalPriceCents} from unit price and quantity. */
    public void recomputeTotal() {
        this.totalPriceCents = this.unitPriceCents * this.quantity;
    }
}
