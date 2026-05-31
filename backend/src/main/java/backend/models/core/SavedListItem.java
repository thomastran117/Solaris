package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "saved_list_items", indexes = {
        @Index(name = "idx_sli_list_id", columnList = "saved_list_id"),
        @Index(name = "idx_sli_product_id", columnList = "product_id")
})
@EntityListeners(AuditingEntityListener.class)
public class SavedListItem {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saved_list_id", nullable = false)
    private SavedList savedList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "variant_id", nullable = true)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private boolean purchased = false;

    @Column(name = "purchased_at")
    private Instant purchasedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant addedAt;
}
