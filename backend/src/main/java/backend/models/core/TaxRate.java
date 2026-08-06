package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A destination-based sales-tax rate for a jurisdiction.
 *
 * <p>Lookup is most-specific-first: an exact {@code (country, state, postalCode)} row wins, then a
 * state-level row ({@code postalCode = ""}), then a country-level default ({@code state = "" } and
 * {@code postalCode = ""}). Wildcards are stored as the empty string rather than NULL so the unique
 * constraint on {@code (country, state, postalCode)} actually prevents duplicate state-/country-level
 * defaults — a unique constraint permits multiple rows when the column is NULL.
 *
 * <p>{@code country} and {@code state} are stored uppercase; callers normalise before lookup/save.
 */
@Entity
@Table(name = "tax_rates", indexes = {
        @Index(name = "idx_tax_rate_lookup", columnList = "country, state, postal_code"),
        @Index(name = "idx_tax_rate_active", columnList = "active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_tax_rate_jurisdiction", columnNames = {"country", "state", "postal_code"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TaxRate {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    /**
     * Optimistic-lock guard: admin edits load-then-save a rate, and the rate is read on every
     * checkout. Without this two concurrent edits could silently overwrite each other.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /** ISO 3166-1 alpha-2 country code, uppercase. */
    @Column(nullable = false, length = 2)
    private String country = "US";

    /** ISO 3166-2 state/subdivision code, uppercase. Empty string = country-wide default. */
    @Column(nullable = false, length = 2)
    private String state = "";

    /** Postal code for ZIP-level overrides. Empty string = applies to the whole state/country. */
    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode = "";

    /** Fractional rate, e.g. {@code 0.08875} for 8.875%. */
    @Column(nullable = false, precision = 6, scale = 5)
    private BigDecimal rate;

    /** Whether shipping cost is taxable in this jurisdiction. */
    @Column(name = "shipping_taxable", nullable = false)
    private boolean shippingTaxable = false;

    /** Inactive rows are ignored by the resolver. */
    @Column(nullable = false)
    private boolean active = true;

    /** Human-readable label, e.g. "California state sales tax". */
    @Column(nullable = true, length = 255)
    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
