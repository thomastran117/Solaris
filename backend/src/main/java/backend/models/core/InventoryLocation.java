package backend.models.core;

import backend.models.enums.LocationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_locations", indexes = {
        @Index(name = "idx_loc_company", columnList = "company_id"),
        @Index(name = "idx_loc_company_code", columnList = "company_id, code", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class InventoryLocation {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 255)
    private String name;

    /** Short machine-readable identifier, e.g. "WH-TORONTO". Unique per company. */
    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = true, length = 500)
    private String address;

    @Column(nullable = true, length = 100)
    private String city;

    /** State / province / region. Used as the origin for carrier rate lookups (Feature 13). */
    @Column(nullable = true, length = 100)
    private String stateProvince;

    /** Postal / ZIP code. Carriers need this for accurate origin rating (Feature 13). */
    @Column(nullable = true, length = 20)
    private String postalCode;

    @Column(nullable = true, length = 100)
    private String country;

    /** Soft disable — does not affect existing fulfillmentLocation FK references on OrderItem. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int displayOrder = 0;

    @Column(nullable = true)
    private Double latitude;

    @Column(nullable = true)
    private Double longitude;

    @Column(nullable = true, precision = 10, scale = 4)
    private BigDecimal fulfillmentCost;

    /**
     * Operational role of the location. WAREHOUSE ships only; STORE supports pickup only;
     * HYBRID does both. Drives availability/pickup display on the storefront.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'WAREHOUSE'")
    private LocationType type = LocationType.WAREHOUSE;

    /** Admin-tunable handling time before a packed item leaves the location. Feeds delivery ETA. */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 1")
    private int handlingDays = 1;

    /** When type != WAREHOUSE: hours until pickup is ready after order. Null for WAREHOUSE. */
    @Column(nullable = true)
    private Integer pickupReadyHours;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
