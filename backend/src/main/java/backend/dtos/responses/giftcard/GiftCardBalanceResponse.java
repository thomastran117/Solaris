package backend.dtos.responses.giftcard;

import backend.models.enums.GiftCardStatus;

public record GiftCardBalanceResponse(
        String code,
        long remainingBalanceCents,
        GiftCardStatus status
) {}
