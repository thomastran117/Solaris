package backend.models.core;

import backend.models.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "stock_notifications",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sn_user_product_variant",
        columnNames = {"user_id", "product_id", "variant_ref"}
    ),
    indexes = {
        @Index(name = "idx_sn_product_variant_status", columnList = "product_id, variant_ref, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class StockNotification {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_ref", insertable = false, updatable = false)
    private ProductVariant variant;

    @Column(name = "variant_ref")
    private UUID variantRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Version
    private int version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant notifiedAt;
}
