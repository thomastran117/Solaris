package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bundle_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bundle_product_variant",
                columnNames = {"bundle_id", "product_id", "variant_id"}),
        indexes = @Index(name = "idx_bundle_item_bundle", columnList = "bundle_id"))
@Getter
@Setter
@NoArgsConstructor
public class BundleItem {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bundle_id", nullable = false)
    private ProductBundle bundle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false)
    private int displayOrder = 0;
}
