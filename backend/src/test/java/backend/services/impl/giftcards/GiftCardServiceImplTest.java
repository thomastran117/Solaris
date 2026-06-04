package backend.services.impl.giftcards;

import backend.dtos.responses.giftcard.GiftCardBalanceResponse;
import backend.dtos.responses.giftcard.GiftCardResponse;
import backend.events.order.GiftCardIssueRequestedEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.GoneException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CustomerCredit;
import backend.models.core.GiftCard;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CreditEntryType;
import backend.models.enums.GiftCardStatus;
import backend.models.enums.ProductType;
import backend.models.enums.UserRole;
import backend.repositories.CustomerCreditRepository;
import backend.repositories.GiftCardRepository;
import backend.repositories.OrderRepository;
import backend.repositories.UserRepository;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GiftCardServiceImplTest {

    private GiftCardRepository giftCardRepository;
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private CustomerCreditRepository creditRepository;
    private EmailService emailService;
    private GiftCardServiceImpl service;

    private static final UUID ORDER_ID   = TestIds.uuid(1);
    private static final UUID ITEM_ID    = TestIds.uuid(2);
    private static final UUID USER_ID    = TestIds.uuid(3);
    private static final UUID COMPANY_ID = TestIds.uuid(4);
    private static final UUID CARD_ID    = TestIds.uuid(5);

    @BeforeEach
    void setUp() {
        giftCardRepository = mock(GiftCardRepository.class);
        orderRepository    = mock(OrderRepository.class);
        userRepository     = mock(UserRepository.class);
        creditRepository   = mock(CustomerCreditRepository.class);
        emailService       = mock(EmailService.class);
        service = new GiftCardServiceImpl(giftCardRepository, orderRepository,
                userRepository, creditRepository, emailService);
    }

    // ─── issueCardsForOrder ───────────────────────────────────────────────────

    @Test
    void issueCards_createsGiftCardForGiftCardItem() {
        Order order = makeOrder(USER_ID);
        OrderItem item = makeGiftCardItem(ITEM_ID, 5000L);
        order.getItems().add(item);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(giftCardRepository.findByPurchasedOnOrderItemId(ITEM_ID)).thenReturn(Optional.empty());
        when(giftCardRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(giftCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.issueCardsForOrder(new GiftCardIssueRequestedEvent(ORDER_ID));

        ArgumentCaptor<GiftCard> captor = ArgumentCaptor.forClass(GiftCard.class);
        verify(giftCardRepository).save(captor.capture());
        GiftCard saved = captor.getValue();
        assertEquals(5000L, saved.getOriginalValueCents());
        assertEquals(5000L, saved.getRemainingBalanceCents());
        assertEquals(GiftCardStatus.ACTIVE, saved.getStatus());
        assertEquals(ORDER_ID, saved.getPurchasedOnOrderId());
        assertEquals(ITEM_ID, saved.getPurchasedOnOrderItemId());
    }

    @Test
    void issueCards_skipsStandardProductItems() {
        Order order = makeOrder(USER_ID);
        OrderItem standardItem = makeStandardItem(TestIds.uuid(10), 2000L);
        order.getItems().add(standardItem);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.issueCardsForOrder(new GiftCardIssueRequestedEvent(ORDER_ID));

        verify(giftCardRepository, never()).save(any());
    }

    @Test
    void issueCards_idempotent_skipsDuplicateIssuance() {
        Order order = makeOrder(USER_ID);
        OrderItem item = makeGiftCardItem(ITEM_ID, 5000L);
        order.getItems().add(item);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(giftCardRepository.findByPurchasedOnOrderItemId(ITEM_ID))
                .thenReturn(Optional.of(new GiftCard())); // already exists

        service.issueCardsForOrder(new GiftCardIssueRequestedEvent(ORDER_ID));

        verify(giftCardRepository, never()).save(any());
    }

    @Test
    void issueCards_orderNotFound_silentlyIgnores() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.issueCardsForOrder(new GiftCardIssueRequestedEvent(ORDER_ID)));
        verify(giftCardRepository, never()).save(any());
    }

    @Test
    void issueCards_mixedOrder_onlyIssuesCardForGiftCardItem() {
        Order order = makeOrder(USER_ID);
        order.getItems().add(makeStandardItem(TestIds.uuid(10), 1000L));
        order.getItems().add(makeGiftCardItem(ITEM_ID, 5000L));

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(giftCardRepository.findByPurchasedOnOrderItemId(ITEM_ID)).thenReturn(Optional.empty());
        when(giftCardRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(giftCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.issueCardsForOrder(new GiftCardIssueRequestedEvent(ORDER_ID));

        verify(giftCardRepository, times(1)).save(any());
    }

    // ─── redeemCode ───────────────────────────────────────────────────────────

    @Test
    void redeemCode_fullRedemption_marksRedeemed() {
        GiftCard card = makeActiveCard(CARD_ID, 5000L, 5000L);
        User user = makeUser(USER_ID);

        when(giftCardRepository.findByCodeWithLock("TESTCODE1234ABCD")).thenReturn(Optional.of(card));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(creditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GiftCardResponse response = service.redeemCode("TESTCODE1234ABCD", USER_ID, 5000L);

        assertEquals(GiftCardStatus.REDEEMED, response.status());
        assertEquals(0L, response.remainingBalanceCents());

        ArgumentCaptor<CustomerCredit> creditCaptor = ArgumentCaptor.forClass(CustomerCredit.class);
        verify(creditRepository).save(creditCaptor.capture());
        assertEquals(5000L, creditCaptor.getValue().getAmountCents());
        assertEquals(CreditEntryType.GIFT_CARD_CREDIT, creditCaptor.getValue().getType());
    }

    @Test
    void redeemCode_partialRedemption_marksPartiallyUsed() {
        GiftCard card = makeActiveCard(CARD_ID, 5000L, 5000L);
        User user = makeUser(USER_ID);

        when(giftCardRepository.findByCodeWithLock("TESTCODE1234ABCD")).thenReturn(Optional.of(card));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(creditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GiftCardResponse response = service.redeemCode("TESTCODE1234ABCD", USER_ID, 3000L);

        assertEquals(GiftCardStatus.PARTIALLY_USED, response.status());
        assertEquals(2000L, response.remainingBalanceCents());
    }

    @Test
    void redeemCode_voidCard_throwsGone() {
        GiftCard card = makeCard(CARD_ID, GiftCardStatus.VOID, 5000L, 5000L);

        when(giftCardRepository.findByCodeWithLock("TESTCODE1234ABCD")).thenReturn(Optional.of(card));

        assertThrows(GoneException.class,
                () -> service.redeemCode("TESTCODE1234ABCD", USER_ID, 1000L));
    }

    @Test
    void redeemCode_alreadyRedeemed_throwsConflict() {
        GiftCard card = makeCard(CARD_ID, GiftCardStatus.REDEEMED, 5000L, 0L);

        when(giftCardRepository.findByCodeWithLock("TESTCODE1234ABCD")).thenReturn(Optional.of(card));

        assertThrows(ConflictException.class,
                () -> service.redeemCode("TESTCODE1234ABCD", USER_ID, 1000L));
    }

    @Test
    void redeemCode_codeNotFound_throwsNotFound() {
        when(giftCardRepository.findByCodeWithLock("DOESNOTEXIST1234")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.redeemCode("DOESNOTEXIST1234", USER_ID, 1000L));
    }

    @Test
    void redeemCode_amountExceedsBalance_throwsBadRequest() {
        GiftCard card = makeActiveCard(CARD_ID, 5000L, 2000L);

        when(giftCardRepository.findByCodeWithLock("TESTCODE1234ABCD")).thenReturn(Optional.of(card));

        assertThrows(BadRequestException.class,
                () -> service.redeemCode("TESTCODE1234ABCD", USER_ID, 3000L));
    }

    @Test
    void redeemCode_zeroAmount_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.redeemCode("TESTCODE1234ABCD", USER_ID, 0L));
    }

    // ─── getBalance ───────────────────────────────────────────────────────────

    @Test
    void getBalance_returnsRemainingBalance() {
        GiftCard card = makeActiveCard(CARD_ID, 5000L, 2000L);
        card.setCode("TESTCODE1234ABCD");

        when(giftCardRepository.findByCode("TESTCODE1234ABCD")).thenReturn(Optional.of(card));

        GiftCardBalanceResponse response = service.getBalance("TESTCODE1234ABCD");

        assertEquals(2000L, response.remainingBalanceCents());
        assertEquals(GiftCardStatus.ACTIVE, response.status());
    }

    @Test
    void getBalance_codeNotFound_throwsNotFound() {
        when(giftCardRepository.findByCode("DOESNOTEXIST1234")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getBalance("DOESNOTEXIST1234"));
    }

    // ─── voidCard ─────────────────────────────────────────────────────────────

    @Test
    void voidCard_activeCard_marksVoid() {
        GiftCard card = makeActiveCard(CARD_ID, 5000L, 5000L);
        when(giftCardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(giftCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.voidCard(CARD_ID);

        ArgumentCaptor<GiftCard> captor = ArgumentCaptor.forClass(GiftCard.class);
        verify(giftCardRepository).save(captor.capture());
        assertEquals(GiftCardStatus.VOID, captor.getValue().getStatus());
    }

    @Test
    void voidCard_alreadyVoid_throwsBadRequest() {
        GiftCard card = makeCard(CARD_ID, GiftCardStatus.VOID, 5000L, 5000L);
        when(giftCardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));

        assertThrows(BadRequestException.class, () -> service.voidCard(CARD_ID));
    }

    @Test
    void voidCard_notFound_throwsNotFound() {
        when(giftCardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.voidCard(CARD_ID));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Order makeOrder(UUID userId) {
        User user = makeUser(userId);
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUser(user);
        return order;
    }

    private OrderItem makeGiftCardItem(UUID itemId, long priceCents) {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Test Store");

        Product product = new Product();
        product.setId(TestIds.uuid(20));
        product.setProductType(ProductType.GIFT_CARD);
        product.setName("$50 Gift Card");
        product.setPrice(BigDecimal.valueOf(priceCents, 2));
        product.setCompany(company);

        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(priceCents, 2));
        return item;
    }

    private OrderItem makeStandardItem(UUID itemId, long priceCents) {
        Product product = new Product();
        product.setId(TestIds.uuid(21));
        product.setProductType(ProductType.STANDARD);
        product.setName("T-Shirt");
        product.setPrice(BigDecimal.valueOf(priceCents, 2));

        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(priceCents, 2));
        return item;
    }

    private GiftCard makeActiveCard(UUID cardId, long original, long remaining) {
        return makeCard(cardId, GiftCardStatus.ACTIVE, original, remaining);
    }

    private GiftCard makeCard(UUID cardId, GiftCardStatus status, long original, long remaining) {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Test Store");

        User user = makeUser(USER_ID);

        GiftCard card = new GiftCard();
        card.setId(cardId);
        card.setCode("TESTCODE1234ABCD");
        card.setCompany(company);
        card.setOriginalValueCents(original);
        card.setRemainingBalanceCents(remaining);
        card.setPurchasedByUser(user);
        card.setStatus(status);
        return card;
    }

    private User makeUser(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setFirstName("Alice");
        user.setRole(UserRole.USER);
        return user;
    }
}
