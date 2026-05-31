package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "loyalty_tiers", indexes = {
        @Index(name = "idx_loyalty_tier_company", columnList = "company_id"),
        @Index(name = "idx_loyalty_tier_company_min", columnList = "company_id, min_points")
})
@EntityListeners(AuditingEntityListener.class)
public class LoyaltyTier {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, name = "company_id", columnDefinition = "BINARY(16)")
    private UUID companyId;

    @Column(nullable = false, length = 60)
    private String name;

    /** Lifetime-points threshold required to reach this tier. */
    @Column(nullable = false, name = "min_points")
    private long minPoints;

    /** Multiplier applied on top of base earn rate. e.g. 2.00 = 2× points. */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal earnMultiplier = BigDecimal.ONE;

    /** JSON blob of perks, e.g. {"freeShipping": true}. Informational only. */
    @Column(nullable = true, length = 1000)
    private String perksJson;

    @Column(nullable = true, length = 20)
    private String badgeColor;

    @Column(nullable = false)
    private int displayOrder = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
