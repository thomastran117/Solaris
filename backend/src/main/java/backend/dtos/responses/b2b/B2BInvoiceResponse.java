package backend.dtos.responses.b2b;

import backend.models.core.B2BInvoice;
import backend.models.enums.InvoiceStatus;

import java.time.Instant;
import java.util.UUID;

public record B2BInvoiceResponse(
        UUID id,
        UUID orderId,
        UUID quoteId,
        UUID vendorCompanyId,
        UUID buyerUserId,
        String invoiceNumber,
        Instant dueDateAt,
        long totalCents,
        InvoiceStatus status,
        Instant paidAt,
        String paymentReference,
        Instant createdAt
) {
    public static B2BInvoiceResponse from(B2BInvoice inv) {
        return new B2BInvoiceResponse(
                inv.getId(),
                inv.getOrderId(),
                inv.getQuoteId(),
                inv.getVendorCompanyId(),
                inv.getBuyerUserId(),
                inv.getInvoiceNumber(),
                inv.getDueDateAt(),
                inv.getTotalCents(),
                inv.getStatus(),
                inv.getPaidAt(),
                inv.getPaymentReference(),
                inv.getCreatedAt()
        );
    }
}
