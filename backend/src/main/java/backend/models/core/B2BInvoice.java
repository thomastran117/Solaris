package backend.models.core;

import backend.models.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Net-terms invoice for a converted B2B order (Feature 12). Tracks payment separately from the
 * order, which is created PAID so it fulfills normally. {@code vendorCompanyId}/{@code buyerUserId}
 * are denormalized so vendor listing and buyer credit-balance sums are single-table queries.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "b2b_invoices", indexes = {
        @Index(name = "idx_b2b_invoice_number", columnList = "invoice_number", unique = true),
        @Index(name = "idx_b2b_invoice_vendor", columnList = "vendor_company_id"),
        @Index(name = "idx_b2b_invoice_buyer", columnList = "buyer_user_id"),
        @Index(name = "idx_b2b_invoice_status", columnList = "status")
})
public class B2BInvoice {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "quote_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID quoteId;

    @Column(name = "vendor_company_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID vendorCompanyId;

    @Column(name = "buyer_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID buyerUserId;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 40)
    private String invoiceNumber;

    @Column(nullable = false)
    private Instant dueDateAt;

    @Column(nullable = false)
    private long totalCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(nullable = true)
    private Instant paidAt;

    @Column(nullable = true, length = 255)
    private String paymentReference;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
