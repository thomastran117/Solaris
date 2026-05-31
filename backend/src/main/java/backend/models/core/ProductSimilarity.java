package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "product_similarity",
        indexes = @Index(name = "idx_similarity_source_score", columnList = "source_product_id, score DESC")
)
public class ProductSimilarity {

    @EmbeddedId
    private ProductSimilarityId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("sourceProductId")
    @JoinColumn(name = "source_product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product sourceProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("targetProductId")
    @JoinColumn(name = "target_product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product targetProduct;

    @Column(nullable = false)
    private float score;

    @Column(name = "match_signals", nullable = false, length = 100)
    private String matchSignals;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public ProductSimilarity(Product source, Product target, float score, String matchSignals) {
        this.id = new ProductSimilarityId(source.getId(), target.getId());
        this.sourceProduct = source;
        this.targetProduct = target;
        this.score = score;
        this.matchSignals = matchSignals;
        this.computedAt = Instant.now();
    }
}
