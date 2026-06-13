package backend.services.impl.giftcards;

import backend.dtos.responses.giftcard.GiftCardBalanceResponse;
import backend.dtos.responses.giftcard.GiftCardResponse;
import backend.events.order.GiftCardIssueRequestedEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.GoneException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CustomerCredit;
import backend.models.core.GiftCard;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.User;
import backend.models.enums.CreditEntryType;
import backend.models.enums.GiftCardStatus;
import backend.models.enums.ProductType;
import backend.repositories.CustomerCreditRepository;
import backend.repositories.GiftCardRepository;
import backend.repositories.OrderRepository;
import backend.repositories.UserRepository;
import backend.services.intf.giftcards.GiftCardService;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class GiftCardServiceImpl implements GiftCardService {

    private static final Logger log = LoggerFactory.getLogger(GiftCardServiceImpl.class);
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 16;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final GiftCardRepository giftCardRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CustomerCreditRepository creditRepository;
    private final EmailService emailService;

    public GiftCardServiceImpl(GiftCardRepository giftCardRepository,
                               OrderRepository orderRepository,
                               UserRepository userRepository,
                               CustomerCreditRepository creditRepository,
                               EmailService emailService) {
        this.giftCardRepository = giftCardRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.creditRepository = creditRepository;
        this.emailService = emailService;
    }

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issueCardsForOrder(GiftCardIssueRequestedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("[GIFT_CARD] Order {} not found during card issuance — skipping", event.orderId());
            return;
        }

        List<OrderItem> items = order.getItems();
        for (OrderItem item : items) {
            if (item.getProduct() == null
                    || item.getProduct().getProductType() != ProductType.GIFT_CARD) {
                continue;
            }

            // Issue one card per purchased unit (a $25 gift card bought x3 yields three $25 cards).
            // Idempotency: count cards already issued for this order item and only issue the rest,
            // so a redelivered issuance event tops up to quantity without duplicating.
            int quantity = Math.max(1, item.getQuantity());
            long alreadyIssued = giftCardRepository.countByPurchasedOnOrderItemId(item.getId());
            if (alreadyIssued >= quantity) {
                log.debug("[GIFT_CARD] All {} card(s) already issued for orderItem {} — skipping", quantity, item.getId());
                continue;
            }

            long valueCents = item.getUnitPrice()
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .longValue();

            for (long n = alreadyIssued; n < quantity; n++) {
                String code = generateUniqueCode();
                GiftCard card = new GiftCard();
                card.setCompany(item.getProduct().getCompany());
                card.setCode(code);
                card.setOriginalValueCents(valueCents);
                card.setRemainingBalanceCents(valueCents);
                card.setPurchasedByUser(order.getUser());
                card.setPurchasedOnOrderId(order.getId());
                card.setPurchasedOnOrderItemId(item.getId());
                card.setStatus(GiftCardStatus.ACTIVE);
                giftCardRepository.save(card);

                log.info("[GIFT_CARD] Issued gift card {} for order {} item {} (unit {}/{})",
                        code, order.getId(), item.getId(), n + 1, quantity);

                try {
                    User buyer = order.getUser();
                    emailService.sendGiftCardIssuedEmail(
                            buyer.getEmail(),
                            buyer.getFirstName(),
                            code,
                            (int) valueCents,
                            item.getProduct().getCompany().getName()
                    );
                } catch (Exception e) {
                    log.error("[GIFT_CARD] Failed to send gift card email for card {}: {}", code, e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional
    public GiftCardResponse redeemCode(String code, UUID userId, long amountCents) {
        if (amountCents <= 0) {
            throw new BadRequestException("Redemption amount must be greater than zero");
        }

        // Acquire pessimistic lock on the code lookup in a single query to close the
        // window between findByCode and findByIdWithLock that allowed concurrent redemptions.
        GiftCard card = giftCardRepository.findByCodeWithLock(code)
                .orElseThrow(() -> new ResourceNotFoundException("Gift card not found: " + code));

        if (card.getStatus() == GiftCardStatus.VOID) {
            throw new GoneException("Gift card has been voided");
        }
        if (card.getStatus() == GiftCardStatus.REDEEMED) {
            throw new ConflictException("Gift card has already been fully redeemed");
        }
        if (amountCents > card.getRemainingBalanceCents()) {
            throw new BadRequestException(
                    "Redemption amount (" + amountCents + " cents) exceeds remaining balance ("
                            + card.getRemainingBalanceCents() + " cents)");
        }

        User redeemer = userRepository.findById(userId)
                .orElseThrow(() -> new backend.exceptions.http.ResourceNotFoundException("User not found"));


        CustomerCredit credit = new CustomerCredit();
        credit.setUser(redeemer);
        credit.setAmountCents(amountCents);
        credit.setType(CreditEntryType.GIFT_CARD_CREDIT);
        credit.setReason("Gift card redemption — code " + card.getCode());
        creditRepository.save(credit);

        card.setRemainingBalanceCents(card.getRemainingBalanceCents() - amountCents);
        card.setRedeemedByUser(redeemer);
        card.setRedeemedAt(Instant.now());
        card.setStatus(card.getRemainingBalanceCents() == 0
                ? GiftCardStatus.REDEEMED
                : GiftCardStatus.PARTIALLY_USED);
        giftCardRepository.save(card);

        log.info("[GIFT_CARD] User {} redeemed {} cents from card {}, remaining={}",
                userId, amountCents, card.getCode(), card.getRemainingBalanceCents());

        return toResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public GiftCardBalanceResponse getBalance(String code) {
        GiftCard card = giftCardRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Gift card not found: " + code));
        return new GiftCardBalanceResponse(card.getCode(), card.getRemainingBalanceCents(), card.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GiftCardResponse> listPurchased(UUID userId, Pageable pageable) {
        return giftCardRepository
                .findAllByPurchasedByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public void voidCard(UUID cardId) {
        GiftCard card = giftCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Gift card not found: " + cardId));
        if (card.getStatus() == GiftCardStatus.VOID) {
            throw new BadRequestException("Gift card is already voided");
        }
        card.setStatus(GiftCardStatus.VOID);
        giftCardRepository.save(card);
        log.info("[GIFT_CARD] Card {} voided", cardId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generateUniqueCode() {
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String candidate = sb.toString();
            if (giftCardRepository.findByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique gift card code after " + MAX_CODE_ATTEMPTS + " attempts");
    }

    private GiftCardResponse toResponse(GiftCard card) {
        return new GiftCardResponse(
                card.getId(),
                card.getCode(),
                card.getCompany().getId(),
                card.getOriginalValueCents(),
                card.getRemainingBalanceCents(),
                card.getPurchasedByUser().getId(),
                card.getPurchasedOnOrderId(),
                card.getStatus(),
                card.getRedeemedAt(),
                card.getCreatedAt()
        );
    }
}
