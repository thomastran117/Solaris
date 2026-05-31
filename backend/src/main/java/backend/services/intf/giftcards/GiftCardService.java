package backend.services.intf.giftcards;

import backend.dtos.responses.giftcard.GiftCardBalanceResponse;
import backend.dtos.responses.giftcard.GiftCardResponse;
import backend.events.order.GiftCardIssueRequestedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GiftCardService {

    /**
     * Issues a {@link backend.models.core.GiftCard} for every GIFT_CARD-type item in the order.
     * Triggered via {@link org.springframework.transaction.event.TransactionalEventListener}
     * after the payment transaction commits.
     */
    void issueCardsForOrder(GiftCardIssueRequestedEvent event);

    /**
     * Partially or fully redeems a gift card, creating a {@link backend.models.core.CustomerCredit}
     * entry of type {@link backend.models.enums.CreditEntryType#GIFT_CARD_CREDIT}.
     */
    GiftCardResponse redeemCode(String code, UUID userId, long amountCents);

    /** Returns remaining balance for a code without requiring authentication. */
    GiftCardBalanceResponse getBalance(String code);

    /** Lists gift cards purchased by the given user, newest first. */
    Page<GiftCardResponse> listPurchased(UUID userId, Pageable pageable);

    /** Voids a card (admin only). Subsequent redemption attempts will receive 410 Gone. */
    void voidCard(UUID cardId);
}
