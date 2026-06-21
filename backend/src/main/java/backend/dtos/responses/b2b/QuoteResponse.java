package backend.dtos.responses.b2b;

import backend.models.core.B2BQuote;
import backend.models.enums.PaymentTerms;
import backend.models.enums.QuoteStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteResponse(
        UUID id,
        UUID vendorCompanyId,
        UUID buyerUserId,
        QuoteStatus status,
        Instant expiresAt,
        String buyerMessage,
        String vendorNote,
        PaymentTerms paymentTerms,
        String currency,
        UUID convertedOrderId,
        long totalCents,
        List<QuoteItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static QuoteResponse from(B2BQuote q) {
        return new QuoteResponse(
                q.getId(),
                q.getVendorCompanyId(),
                q.getBuyerUserId(),
                q.getStatus(),
                q.getExpiresAt(),
                q.getBuyerMessage(),
                q.getVendorNote(),
                q.getPaymentTerms(),
                q.getCurrency(),
                q.getConvertedOrderId(),
                q.totalCents(),
                q.getItems().stream().map(QuoteItemResponse::from).toList(),
                q.getCreatedAt(),
                q.getUpdatedAt()
        );
    }
}
