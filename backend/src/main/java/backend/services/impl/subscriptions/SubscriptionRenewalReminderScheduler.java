package backend.services.impl.subscriptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import backend.models.core.Subscription;
import backend.models.core.User;
import backend.models.enums.SubscriptionStatus;
import backend.repositories.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sends a heads-up email two to three days before each ACTIVE subscription's
 * next billing date. Stripe handles the actual billing — this scheduler only
 * surfaces upcoming charges so customers can pause / skip / swap before they hit.
 */
@Component
public class SubscriptionRenewalReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalReminderScheduler.class);

    @Value("${app.subscription.dunning.grace-period-days:14}")
    private int dunningGracePeriodDays;

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionRenewalReminderScheduler(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Scheduled(cron = "${app.subscription.reminder.cron:0 0 8 * * *}")
    public void sendRenewalReminders() {
        Instant from = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(3, ChronoUnit.DAYS);

        List<Subscription> upcoming = subscriptionRepository.findAllByStatusAndNextBillingAtBetween(
                SubscriptionStatus.ACTIVE, from, to);

        if (upcoming.isEmpty()) return;

        log.info("Sending renewal reminders for {} subscriptions", upcoming.size());

        for (Subscription sub : upcoming) {
            try {
                User user = sub.getUser();
                if (user == null || user.getEmail() == null) continue;
                // Dedicated EmailService template will be wired in a follow-up; for now
                // log the intent so ops can audit until the template is added.
                log.info("Renewal reminder due for subscription {} (user {} email {}) at {}",
                        sub.getId(), user.getId(), user.getEmail(), sub.getNextBillingAt());
            } catch (Exception e) {
                log.warn("Renewal reminder failed for subscription {}: {}", sub.getId(), e.getMessage());
            }
        }
    }

    /**
     * Auto-cancels subscriptions that have been PAST_DUE longer than
     * {@code app.subscription.dunning.grace-period-days} (default 14). Stripe drives
     * the actual billing dunning; this pass handles the local status update for
     * subscriptions whose {@code customer.subscription.deleted} webhook was missed or
     * whose Stripe dunning cycle ended without a status-change event reaching us.
     */
    @Scheduled(cron = "${app.subscription.dunning.cron:0 0 6 * * *}")
    @Transactional
    public void cancelExpiredPastDueSubscriptions() {
        Instant cutoff = Instant.now().minus(dunningGracePeriodDays, ChronoUnit.DAYS);
        List<Subscription> expired = subscriptionRepository
                .findAllByStatusAndPastDueSinceBefore(SubscriptionStatus.PAST_DUE, cutoff);

        if (expired.isEmpty()) return;

        log.info("[DUNNING] Auto-cancelling {} subscriptions past grace period ({} days)",
                expired.size(), dunningGracePeriodDays);

        for (Subscription sub : expired) {
            try {
                sub.setStatus(SubscriptionStatus.CANCELLED);
                sub.setCancelledAt(Instant.now());
                sub.setNextBillingAt(null);
                subscriptionRepository.save(sub);
                log.info("[DUNNING] Cancelled subscription {} (pastDueSince={})", sub.getId(), sub.getPastDueSince());
            } catch (Exception e) {
                log.error("[DUNNING] Failed to cancel subscription {}: {}", sub.getId(), e.getMessage());
            }
        }
    }
}
