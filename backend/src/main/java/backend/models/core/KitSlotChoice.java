package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "kit_slot_choices",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_choice_slot_product_variant",
                columnNames = {"slot_id", "product_id", "variant_id"}),
        indexes = @Index(name = "idx_choice_slot", columnList = "slot_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class KitSlotChoice {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private KitSlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    /** Optional surcharge (+) or discount (–) on top of the component's base price. */
    @Column(nullable = true, precision = 12, scale = 2)
    private BigDecimal priceDelta;

    @Column(nullable = false)
    private boolean defaultChoice = false;

    @Column(nullable = false)
    private int displayOrder = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
