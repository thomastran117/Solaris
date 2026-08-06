package backend.models.core;

import backend.models.enums.ReturnItemCondition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "return_items", indexes = {
        @Index(name = "idx_return_item_return",     columnList = "return_id"),
        @Index(name = "idx_return_item_order_item", columnList = "order_item_id")
})
public class ReturnItem {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private Return returnRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /**
     * Quantity being returned from this order item. Must be ≥ 1 and ≤ orderItem.quantity
     * minus previously queued/approved quantities. Enables partial-quantity returns
     * (e.g., return 1 of 3 units ordered).
     */
    @Column(nullable = false)
    private int quantityReturned;

    /** Whether stock was successfully restored for this item during return processing. */
    @Column(nullable = false)
    private boolean stockRestored = false;

    /**
     * Condition recorded by the merchant during inspection (APPROVED → COMPLETED transition).
     * Null until inspectReturn() is called.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    private ReturnItemCondition condition;
}
