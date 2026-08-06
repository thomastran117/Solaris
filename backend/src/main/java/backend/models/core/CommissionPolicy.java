package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "commission_policies", indexes = {
        @Index(name = "idx_commission_policy_marketplace", columnList = "marketplace_id"),
        @Index(name = "idx_commission_policy_active", columnList = "marketplace_id, active")
})
@EntityListeners(AuditingEntityListener.class)
public class CommissionPolicy {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @Column(nullable = false, name = "marketplace_id")
    private java.util.UUID marketplaceId;

    @Column(nullable = false, length = 255)
    private String name;

    /** Default take-rate when no rule matches. E.g. 0.1500 = 15%. */
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal defaultRate;

    @Column(nullable = true)
    private Instant effectiveFrom;

    @Column(nullable = true)
    private Instant effectiveTo;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("priority DESC")
    private List<CommissionRule> rules = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
