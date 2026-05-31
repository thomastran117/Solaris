package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import backend.models.enums.CommissionRuleType;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "commission_rules", indexes = {
        @Index(name = "idx_commission_rule_policy", columnList = "policy_id")
})
public class CommissionRule {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private CommissionPolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommissionRuleType ruleType;

    /** Value to match against: category name, brand, vendor tier name, SKU prefix, or min-volume amount. */
    @Column(nullable = false, length = 255)
    private String matchValue;

    /** Commission rate to apply when this rule matches. E.g. 0.0800 = 8%. */
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal rate;

    /** Higher priority wins. Ties are broken by ID (lower ID wins). */
    @Column(nullable = false)
    private int priority = 0;
}
