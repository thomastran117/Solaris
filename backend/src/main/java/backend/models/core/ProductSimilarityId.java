package backend.models.core;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProductSimilarityId implements Serializable {

    @Column(name = "source_product_id", nullable = false)
    private UUID sourceProductId;

    @Column(name = "target_product_id", nullable = false)
    private UUID targetProductId;
}
