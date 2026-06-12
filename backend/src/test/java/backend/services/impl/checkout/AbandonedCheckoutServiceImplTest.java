package backend.services.impl.checkout;

import backend.events.email.EmailEvent;
import backend.events.order.OrderReservationExpiredEvent;
import backend.models.core.AbandonedCartRecovery;
import backend.repositories.AbandonedCartRecoveryRepository;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AbandonedCheckoutServiceImplTest {

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID ORDER_ID   = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);

    private AbandonedCartRecoveryRepository recoveryRepository;
    private EmailService emailService;
    private AbandonedCheckoutServiceImpl service;

    @BeforeEach
    void setUp() {
        recoveryRepository = mock(AbandonedCartRecoveryRepository.class);
        emailService       = mock(EmailService.class);
        service = new AbandonedCheckoutServiceImpl(recoveryRepository, emailService);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private OrderReservationExpiredEvent event(BigDecimal unitPrice) {
        var item = new OrderReservationExpiredEvent.AbandonedItemData(
                PRODUCT_ID, "Widget", "https://cdn/img.png", unitPrice);
        return new OrderReservationExpiredEvent(
                ORDER_ID, USER_ID, "user@example.com", "Alice", List.of(item));
    }

    private OrderReservationExpiredEvent eventWithItems(List<OrderReservationExpiredEvent.AbandonedItemData> items) {
        return new OrderReservationExpiredEvent(
                ORDER_ID, USER_ID, "user@example.com", "Alice", items);
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Test
    void handleExpiredReservation_eligibleUser_writesRecoveryRowAndPublishesEmail() {
        when(recoveryRepository.existsByUserIdAndSentDate(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(false);
        when(recoveryRepository.save(any(AbandonedCartRecovery.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleExpiredReservation(event(new BigDecimal("19.99")));

        verify(recoveryRepository).save(any(AbandonedCartRecovery.class));
        verify(emailService).sendAbandonedCartEmail(
                eq("user@example.com"), eq("Alice"), eq(USER_ID), eq(ORDER_ID), anyList());
    }

    @Test
    void handleExpiredReservation_alreadySentToday_skipsWriteAndEmail() {
        when(recoveryRepository.existsByUserIdAndSentDate(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(true);

        service.handleExpiredReservation(event(new BigDecimal("19.99")));

        verify(recoveryRepository, never()).save(any());
        verify(emailService, never()).sendAbandonedCartEmail(any(), any(), any(), any(), any());
    }

    @Test
    void handleExpiredReservation_concurrentWrite_suppressesDuplicateWithoutException() {
        when(recoveryRepository.existsByUserIdAndSentDate(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(false);
        when(recoveryRepository.save(any(AbandonedCartRecovery.class)))
                .thenThrow(new DataIntegrityViolationException("uk_abandoned_recovery_user_date"));

        assertDoesNotThrow(() -> service.handleExpiredReservation(event(new BigDecimal("19.99"))));

        verify(emailService, never()).sendAbandonedCartEmail(any(), any(), any(), any(), any());
    }

    @Test
    void handleExpiredReservation_mapsItemPriceCentsCorrectly() {
        when(recoveryRepository.existsByUserIdAndSentDate(any(), any())).thenReturn(false);
        when(recoveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleExpiredReservation(event(new BigDecimal("29.99")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmailEvent.AbandonedItem>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(emailService).sendAbandonedCartEmail(any(), any(), any(), any(), captor.capture());

        List<EmailEvent.AbandonedItem> items = captor.getValue();
        assertEquals(1, items.size());
        assertEquals(PRODUCT_ID, items.get(0).productId());
        assertEquals("Widget", items.get(0).name());
        assertEquals("https://cdn/img.png", items.get(0).imageUrl());
        assertEquals(2999L, items.get(0).priceCents());
    }

    @Test
    void handleExpiredReservation_multipleItems_allMapped() {
        var item1 = new OrderReservationExpiredEvent.AbandonedItemData(
                PRODUCT_ID, "Widget", null, new BigDecimal("10.00"));
        var item2 = new OrderReservationExpiredEvent.AbandonedItemData(
                TestIds.uuid(4), "Gadget", "https://cdn/g.png", new BigDecimal("5.50"));
        when(recoveryRepository.existsByUserIdAndSentDate(any(), any())).thenReturn(false);
        when(recoveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleExpiredReservation(eventWithItems(List.of(item1, item2)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmailEvent.AbandonedItem>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(emailService).sendAbandonedCartEmail(any(), any(), any(), any(), captor.capture());

        List<EmailEvent.AbandonedItem> sent = captor.getValue();
        assertEquals(2, sent.size());
        assertEquals(1000L, sent.get(0).priceCents());
        assertEquals(550L,  sent.get(1).priceCents());
    }

    @Test
    void onReservationExpired_delegatesToHandleExpiredReservation() {
        when(recoveryRepository.existsByUserIdAndSentDate(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(false);
        when(recoveryRepository.save(any(AbandonedCartRecovery.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.onReservationExpired(event(new BigDecimal("9.99")));

        verify(recoveryRepository).save(any(AbandonedCartRecovery.class));
        verify(emailService).sendAbandonedCartEmail(any(), any(), any(), any(), anyList());
    }

    @Test
    void handleExpiredReservation_recoveryRowHasCorrectFields() {
        when(recoveryRepository.existsByUserIdAndSentDate(any(), any())).thenReturn(false);
        when(recoveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate before = LocalDate.now();
        service.handleExpiredReservation(event(BigDecimal.ONE));

        ArgumentCaptor<AbandonedCartRecovery> captor =
                ArgumentCaptor.forClass(AbandonedCartRecovery.class);
        verify(recoveryRepository).save(captor.capture());

        AbandonedCartRecovery saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(ORDER_ID, saved.getOrderId());
        assertFalse(saved.getSentDate().isBefore(before));
        assertFalse(saved.getSentDate().isAfter(LocalDate.now()));
    }
}
