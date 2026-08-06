package backend.models.core;

import backend.models.enums.PaymentTerms;
import backend.models.enums.QuoteStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A direct vendor-to-buyer wholesale quote (Feature 12). Carries negotiated line-item prices that
 * bypass the promotion engine when converted to an {@link Order}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "b2b_quotes", indexes = {
        @Index(name = "idx_b2b_quote_vendor", columnList = "vendor_company_id"),
        @Index(name = "idx_b2b_quote_buyer", columnList = "buyer_user_id"),
        @Index(name = "idx_b2b_quote_status", columnList = "status")
})
public class B2BQuote {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "vendor_company_id", nullable = false)
    private UUID vendorCompanyId;

    @Column(name = "buyer_user_id", nullable = false)
    private UUID buyerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.PENDING_VENDOR;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = true, length = 2000)
    private String buyerMessage;

    @Column(nullable = true, length = 2000)
    private String vendorNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTerms paymentTerms = PaymentTerms.IMMEDIATE;

    /** ISO 4217 currency snapshotted from the quoted products, used for the converted order. */
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "converted_order_id", nullable = true)
    private UUID convertedOrderId;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<B2BQuoteItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** Sum of line totals across all items, in cents. */
    public long totalCents() {
        return items.stream().mapToLong(B2BQuoteItem::getTotalPriceCents).sum();
    }
}
