package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import backend.models.enums.DiscountStatus;
import backend.models.enums.PromotionRuleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "promotion_rules", indexes = {
        @Index(name = "idx_promorule_company",  columnList = "company_id"),
        @Index(name = "idx_promorule_status",   columnList = "status"),
        @Index(name = "idx_promorule_end_date", columnList = "end_date"),
        @Index(name = "idx_promorule_type",     columnList = "rule_type")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class PromotionRule {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    /** Owning vendor. All CRUD is scoped to this company. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = true, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private PromotionRuleType ruleType;

    /** Stored status: ACTIVE or DISABLED only. EXPIRED computed at response time. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountStatus status = DiscountStatus.ACTIVE;

    /** Lower = applied first. Default 100. */
    @Column(nullable = false)
    private int priority = 100;

    /** When false, this rule is mutually exclusive with other non-stackable rules in the same pass. */
    @Column(nullable = false)
    private boolean stackable = false;

    /** Null = immediately effective. */
    @Column(nullable = true)
    private Instant startDate;

    /** Null = never expires. */
    @Column(nullable = true)
    private Instant endDate;

    /** Minimum pre-promotion cart subtotal required. Null = no minimum. */
    @Column(nullable = true, precision = 12, scale = 2)
    private BigDecimal minCartAmount;

    /** Total redemption cap across all users. Null = unlimited. */
    @Column(nullable = true)
    private Integer maxUses;

    @Column(nullable = false)
    private int usedCount = 0;

    /** Per-user redemption cap. Null = unlimited. */
    @Column(nullable = true)
    private Integer maxUsesPerUser;

    /**
     * Vendor that absorbs the discount cost. Null = same as {@link #company}.
     * Set by platform admins for marketplace-funded promotions.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "funded_by_company_id", nullable = true)
    private Company fundedByCompany;

    /**
     * Per-type configuration JSON — shape depends on ruleType.
     * Parsed in PricingEngine via Jackson into typed records under backend.services.pricing.config.
     * Stored as native MySQL JSON.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "json")
    private String configJson;

    /**
     * Product set this rule applies to. Empty = entire company catalogue.
     * No cascade — deleting a rule does not delete products.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "promotion_rule_products",
            joinColumns = @JoinColumn(name = "promotion_rule_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    @BatchSize(size = 50)
    private Set<Product> targetProducts = new HashSet<>();

    /**
     * Bundle set this rule applies to. Empty = not bundle-scoped (fires on products via targetProducts).
     * When non-empty, the rule fires only when the cart contains a matching bundle line.
     * A rule with both targetProducts and targetBundles fires on either (union).
     * No cascade — deleting a rule does not delete bundles.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "promotion_rule_bundles",
            joinColumns = @JoinColumn(name = "promotion_rule_id"),
            inverseJoinColumns = @JoinColumn(name = "bundle_id")
    )
    @BatchSize(size = 50)
    private Set<ProductBundle> targetBundles = new HashSet<>();

    /**
     * Segments whose members are eligible. Empty = all users including anonymous.
     * Anonymous callers only see rules with an empty segment set.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "promotion_rule_segments",
            joinColumns = @JoinColumn(name = "promotion_rule_id"),
            inverseJoinColumns = @JoinColumn(name = "segment_id")
    )
    @BatchSize(size = 50)
    private Set<CustomerSegment> targetSegments = new HashSet<>();

    /**
     * Set by {@code DiscountToPromotionRuleMigrator} for one-time migration.
     * Used to detect already-migrated rows and for audit traceability.
     */
    @Column(name = "legacy_discount_id", nullable = true, unique = true)
    private Long legacyDiscountId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
