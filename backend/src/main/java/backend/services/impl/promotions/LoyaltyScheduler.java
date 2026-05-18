package backend.services.impl.promotions;

import java.util.UUID;
import backend.models.core.LoyaltyAccount;
import backend.models.core.LoyaltyPolicy;
import backend.repositories.LoyaltyAccountRepository;
import backend.repositories.LoyaltyPolicyRepository;
import backend.repositories.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.MonthDay;
import java.util.List;

@Component
public class LoyaltyScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyScheduler.class);

    private final LoyaltyPolicyRepository policyRepository;
    private final LoyaltyAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LoyaltyServiceImpl loyaltyService;

    public LoyaltyScheduler(
            LoyaltyPolicyRepository policyRepository,
            LoyaltyAccountRepository accountRepository,
            UserRepository userRepository,
            LoyaltyServiceImpl loyaltyService) {
        this.policyRepository = policyRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.loyaltyService = loyaltyService;
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
