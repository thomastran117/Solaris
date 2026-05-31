package backend.dtos.responses.giftcard;

import backend.models.enums.GiftCardStatus;

import java.time.Instant;
import java.util.UUID;

public record GiftCardResponse(
        UUID id,
        String code,
        UUID companyId,
        long originalValueCents,
        long remainingBalanceCents,
        UUID purchasedByUserId,
        UUID purchasedOnOrderId,
        GiftCardStatus status,
        Instant redeemedAt,
        Instant createdAt
) {}
