package backend.services.impl.subscriptions;

import backend.models.core.Subscription;
import backend.models.core.User;
import backend.models.enums.BillingInterval;
import backend.models.enums.SubscriptionStatus;
import backend.repositories.SubscriptionRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionRenewalReminderSchedulerTest {

    private SubscriptionRepository subscriptionRepository;
    private SubscriptionRenewalReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        scheduler = new SubscriptionRenewalReminderScheduler(subscriptionRepository);
    }

    // ─── sendRenewalReminders ─────────────────────────────────────────────────

    @Test
    void sendRenewalReminders_emptyList_doesNothing() {
        when(subscriptionRepository.findAllByStatusAndNextBillingAtBetween(
                eq(SubscriptionStatus.ACTIVE), any(), any()))
                .thenReturn(List.of());

        scheduler.sendRenewalReminders();

        verify(subscriptionRepository).findAllByStatusAndNextBillingAtBetween(
                eq(SubscriptionStatus.ACTIVE), any(), any());
        verifyNoMoreInteractions(subscriptionRepository);
    }

    @Test
    void sendRenewalReminders_upcomingSubscriptions_processesAll() {
        List<Subscription> upcoming = List.of(
                makeSubscription(TestIds.uuid(1), "user1@example.com"),
                makeSubscription(TestIds.uuid(2), "user2@example.com"));
        when(subscriptionRepository.findAllByStatusAndNextBillingAtBetween(
                eq(SubscriptionStatus.ACTIVE), any(), any()))
                .thenReturn(upcoming);

        assertDoesNotThrow(() -> scheduler.sendRenewalReminders());

        verify(subscriptionRepository).findAllByStatusAndNextBillingAtBetween(
                eq(SubscriptionStatus.ACTIVE), any(), any());
    }

    @Test
    void sendRenewalReminders_subscriptionWithNullUser_skipsGracefully() {
        Subscription sub = makeSubscription(TestIds.uuid(1), "u@example.com");
        sub.setUser(null);
        when(subscriptionRepository.findAllByStatusAndNextBillingAtBetween(any(), any(), any()))
                .thenReturn(List.of(sub));

        assertDoesNotThrow(() -> scheduler.sendRenewalReminders());
    }

    @Test
    void sendRenewalReminders_subscriptionWithNullEmail_skipsGracefully() {
        Subscription sub = makeSubscription(TestIds.uuid(1), null);
        when(subscriptionRepository.findAllByStatusAndNextBillingAtBetween(any(), any(), any()))
                .thenReturn(List.of(sub));

        assertDoesNotThrow(() -> scheduler.sendRenewalReminders());
    }

    // ─── cancelExpiredPastDueSubscriptions ───────────────────────────────────

    @Test
    void cancelExpiredPastDueSubscriptions_emptyList_doesNothing() {
        when(subscriptionRepository.findAllByStatusAndPastDueSinceBefore(
                eq(SubscriptionStatus.PAST_DUE), any()))
                .thenReturn(List.of());

        scheduler.cancelExpiredPastDueSubscriptions();

        verify(subscriptionRepository).findAllByStatusAndPastDueSinceBefore(
                eq(SubscriptionStatus.PAST_DUE), any());
        verifyNoMoreInteractions(subscriptionRepository);
    }

    @Test
    void cancelExpiredPastDueSubscriptions_expiredSubs_cancelsEach() {
        Subscription sub1 = makeSubscription(TestIds.uuid(1), "u1@example.com");
        sub1.setStatus(SubscriptionStatus.PAST_DUE);
        Subscription sub2 = makeSubscription(TestIds.uuid(2), "u2@example.com");
        sub2.setStatus(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findAllByStatusAndPastDueSinceBefore(
                eq(SubscriptionStatus.PAST_DUE), any()))
                .thenReturn(List.of(sub1, sub2));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.cancelExpiredPastDueSubscriptions();

        verify(subscriptionRepository, times(2)).save(any(Subscription.class));
        assertEquals(SubscriptionStatus.CANCELLED, sub1.getStatus());
        assertNotNull(sub1.getCancelledAt());
        assertNull(sub1.getNextBillingAt());
        assertEquals(SubscriptionStatus.CANCELLED, sub2.getStatus());
    }

    @Test
    void cancelExpiredPastDueSubscriptions_saveFailure_continuesWithRemainder() {
        Subscription sub1 = makeSubscription(TestIds.uuid(1), "u1@example.com");
        sub1.setStatus(SubscriptionStatus.PAST_DUE);
        Subscription sub2 = makeSubscription(TestIds.uuid(2), "u2@example.com");
        sub2.setStatus(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findAllByStatusAndPastDueSinceBefore(any(), any()))
                .thenReturn(List.of(sub1, sub2));
        when(subscriptionRepository.save(any()))
                .thenThrow(new RuntimeException("DB error"))
                .thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> scheduler.cancelExpiredPastDueSubscriptions());

        // Despite sub1 save failure, sub2 is still processed
        assertEquals(SubscriptionStatus.CANCELLED, sub2.getStatus());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Subscription makeSubscription(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);

        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setUser(user);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBillingInterval(BillingInterval.MONTH);
        sub.setIntervalCount(1);
        sub.setNextBillingAt(Instant.now().plus(2, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS));
        sub.setPastDueSince(Instant.now().minus(20, ChronoUnit.DAYS));
        sub.setItems(new ArrayList<>());
        return sub;
    }
}
