package backend.services.impl.promotions;

import java.util.UUID;
import backend.events.loyalty.LoyaltyEvent;
import backend.models.core.LoyaltyAccount;
import backend.models.core.LoyaltyPolicy;
import backend.models.core.LoyaltyTransaction;
import backend.repositories.LoyaltyAccountRepository;
import backend.repositories.LoyaltyPolicyRepository;
import backend.repositories.LoyaltyTransactionRepository;
import backend.repositories.UserRepository;
import backend.services.intf.promotions.LoyaltyEventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class LoyaltyScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyScheduler.class);

    private final LoyaltyPolicyRepository policyRepository;
    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final LoyaltyServiceImpl loyaltyService;
    private final LoyaltyEventPublisher loyaltyEventPublisher;

    public LoyaltyScheduler(
            LoyaltyPolicyRepository policyRepository,
            LoyaltyAccountRepository accountRepository,
            LoyaltyTransactionRepository transactionRepository,
            UserRepository userRepository,
            LoyaltyServiceImpl loyaltyService,
            LoyaltyEventPublisher loyaltyEventPublisher) {
        this.policyRepository = policyRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.loyaltyService = loyaltyService;
        this.loyaltyEventPublisher = loyaltyEventPublisher;
    }

    /**
     * Daily birthday reward run (configurable via app.loyalty.birthday.cron, default: 9 AM every day).
     * Finds users with today's birth month/day who have a loyalty account and haven't received
     * this year's birthday reward yet, then issues their reward.
     */
    @Scheduled(cron = "${app.loyalty.birthday.cron:0 0 9 * * *}")
    public void processBirthdayRewards() {
        log.info("[LOYALTY SCHEDULER] Starting birthday reward run");
        List<LoyaltyPolicy> policies = policyRepository.findAllByActiveTrue();

        for (LoyaltyPolicy policy : policies) {
            if (policy.getBirthdayBonusPoints() == 0 && policy.getBirthdayBonusCreditCents() == 0) {
                continue; // birthday rewards disabled for this company
            }

            try {
                processBirthdayRewardsForPolicy(policy);
            } catch (Exception e) {
                log.error("[LOYALTY SCHEDULER] Error processing birthday rewards for policy {} (company {}): {}",
                        policy.getId(), policy.getCompanyId(), e.getMessage());
            }
        }
        log.info("[LOYALTY SCHEDULER] Birthday reward run complete");
    }

    /**
     * Daily expiry-warning run (default: 8 AM every day).
     * Publishes a LoyaltyEvent.PointsExpiringSoon for each account that has points
     * expiring within the next 7 days so downstream consumers (e.g. email service) can notify users.
     */
    @Scheduled(cron = "${app.loyalty.expiry-warning.cron:0 0 8 * * *}")
    public void publishExpiryWarnings() {
        log.info("[LOYALTY SCHEDULER] Starting expiry-warning run");
        Instant now = Instant.now();
        Instant threshold = now.plus(7, ChronoUnit.DAYS);

        List<UUID> accountIds = transactionRepository.findAccountIdsWithPointsExpiringSoon(now, threshold);
        log.info("[LOYALTY SCHEDULER] {} accounts have points expiring within 7 days", accountIds.size());

        for (UUID accountId : accountIds) {
            try {
                LoyaltyAccount account = accountRepository.findById(accountId).orElse(null);
                if (account == null) continue;

                List<LoyaltyTransaction> expiring = transactionRepository.findPointsExpiringSoon(accountId, now, threshold);
                long totalExpiring = expiring.stream().mapToLong(LoyaltyTransaction::getPointsDelta).sum();
                Instant earliest = expiring.stream()
                        .map(LoyaltyTransaction::getExpiresAt)
                        .filter(java.util.Objects::nonNull)
                        .min(Instant::compareTo)
                        .orElse(null);

                if (totalExpiring > 0 && earliest != null) {
                    loyaltyEventPublisher.publish(new LoyaltyEvent.PointsExpiringSoon(
                            account.getUserId(), account.getCompanyId(), totalExpiring, earliest));
                }
            } catch (Exception e) {
                log.error("[LOYALTY SCHEDULER] Error publishing expiry warning for account {}: {}", accountId, e.getMessage());
            }
        }
        log.info("[LOYALTY SCHEDULER] Expiry-warning run complete");
    }

    /**
     * Daily points expiry run (configurable via app.loyalty.expiry.cron, default: 3 AM every day).
     * Finds earn transactions whose expiresAt has passed and issues offsetting EXPIRE entries.
     */
    @Scheduled(cron = "${app.loyalty.expiry.cron:0 0 3 * * *}")
    public void expirePoints() {
        log.info("[LOYALTY SCHEDULER] Starting points expiry run");
        List<UUID> accountIds = loyaltyService.findAccountIdsWithExpiredPoints();

        log.info("[LOYALTY SCHEDULER] {} accounts have expired points to process", accountIds.size());

        for (UUID accountId : accountIds) {
            try {
                LoyaltyAccount account = accountRepository.findById(accountId).orElse(null);
                if (account == null || account.getPointsBalance() <= 0) continue;
                loyaltyService.expireAccountPoints(account);
            } catch (Exception e) {
                log.error("[LOYALTY SCHEDULER] Error expiring points for account {}: {}", accountId, e.getMessage());
            }
        }
        log.info("[LOYALTY SCHEDULER] Points expiry run complete");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void processBirthdayRewardsForPolicy(LoyaltyPolicy policy) {
        MonthDay today = MonthDay.now();
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();

        // Find users in this company's loyalty program whose birthday is today
        List<UUID> birthdayUserIds = userRepository.findUserIdsWithBirthday(month, day);
        if (birthdayUserIds.isEmpty()) return;

        log.info("[LOYALTY SCHEDULER] Policy {} (company {}) — {} users have birthdays today",
                policy.getId(), policy.getCompanyId(), birthdayUserIds.size());

        for (UUID userId : birthdayUserIds) {
            try {
                LoyaltyAccount account = accountRepository
                        .findByUserIdAndCompanyId(userId, policy.getCompanyId())
                        .orElse(null);
                if (account == null) continue; // user not enrolled in this company's program

                loyaltyService.issueBirthdayReward(account, policy);
            } catch (Exception e) {
                log.error("[LOYALTY SCHEDULER] Error issuing birthday reward for user {} policy {}: {}",
                        userId, policy.getId(), e.getMessage());
            }
        }
    }
}
