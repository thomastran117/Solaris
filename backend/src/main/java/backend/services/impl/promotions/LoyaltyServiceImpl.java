package backend.services.impl.promotions;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.credit.IssueCreditRequest;
import backend.dtos.requests.loyalty.AdjustPointsRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyPolicyRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyTierRequest;
import backend.dtos.requests.loyalty.IssueBonusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.loyalty.LoyaltyAccountResponse;
import backend.dtos.responses.loyalty.LoyaltyExpiryWarningResponse;
import backend.dtos.responses.loyalty.LoyaltyPolicyResponse;
import backend.dtos.responses.loyalty.LoyaltyRedemptionQuoteResponse;
import backend.dtos.responses.loyalty.LoyaltyReferralResponse;
import backend.dtos.responses.loyalty.LoyaltyTierResponse;
import backend.dtos.responses.loyalty.LoyaltyTransactionResponse;
import backend.events.loyalty.LoyaltyEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.LoyaltyAccount;
import backend.models.core.LoyaltyPolicy;
import backend.models.core.LoyaltyTier;
import backend.models.core.LoyaltyTransaction;
import backend.models.core.Order;
import backend.models.core.ReferralConversion;
import backend.models.enums.CreditEntryType;
import backend.models.enums.LoyaltyEarnMode;
import backend.models.enums.LoyaltyTransactionType;
import backend.repositories.LoyaltyAccountRepository;
import backend.repositories.ReferralConversionRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.models.enums.CompanyCapability;
import backend.services.intf.customers.CustomerCreditService;
import backend.repositories.LoyaltyPolicyRepository;
import backend.repositories.LoyaltyTierRepository;
import backend.repositories.LoyaltyTransactionRepository;
import backend.services.intf.promotions.LoyaltyEventPublisher;
import backend.services.intf.promotions.LoyaltyService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LoyaltyServiceImpl implements LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyServiceImpl.class);
    private static final String REFERRAL_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REFERRAL_CODE_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyPolicyRepository policyRepository;
    private final LoyaltyTierRepository tierRepository;
    private final ReferralConversionRepository referralConversionRepository;
    private final CompanyAccessService companyAccessService;
    private final CustomerCreditService customerCreditService;
    private final LoyaltyEventPublisher loyaltyEventPublisher;

    public LoyaltyServiceImpl(
            LoyaltyAccountRepository accountRepository,
            LoyaltyTransactionRepository transactionRepository,
            LoyaltyPolicyRepository policyRepository,
            LoyaltyTierRepository tierRepository,
            ReferralConversionRepository referralConversionRepository,
            CompanyAccessService companyAccessService,
            CustomerCreditService customerCreditService,
            LoyaltyEventPublisher loyaltyEventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.policyRepository = policyRepository;
        this.tierRepository = tierRepository;
        this.referralConversionRepository = referralConversionRepository;
        this.companyAccessService = companyAccessService;
        this.customerCreditService = customerCreditService;
        this.loyaltyEventPublisher = loyaltyEventPublisher;
    }

    // -------------------------------------------------------------------------
    // Customer self-service
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getAccount(UUID userId, UUID companyId) {
        LoyaltyAccount account = accountRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElseGet(() -> emptyAccount(userId, companyId));
        return toAccountResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<LoyaltyTransactionResponse> getTransactions(UUID userId, UUID companyId, int page, int size) {
        int cap = Math.min(size, 50);
        LoyaltyAccount account = accountRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No loyalty account found"));
        var pageable = PageRequest.of(page, cap, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PagedResponse<>(transactionRepository.findByAccountId(account.getId(), pageable)
                .map(this::toTransactionResponse));
    }

    @Override
    @Transactional
    public LoyaltyExpiryWarningResponse getExpiryWarning(UUID userId, UUID companyId, int days) {
        LoyaltyAccount account = accountRepository.findByUserIdAndCompanyId(userId, companyId).orElse(null);
        if (account == null) return new LoyaltyExpiryWarningResponse(0L, null);

        Instant from = Instant.now();
        Instant to = from.plus(days, ChronoUnit.DAYS);
        List<LoyaltyTransaction> expiring = transactionRepository.findPointsExpiringSoon(account.getId(), from, to);
        long total = expiring.stream().mapToLong(LoyaltyTransaction::getPointsDelta).sum();
        Instant earliest = expiring.stream()
                .map(LoyaltyTransaction::getExpiresAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        return new LoyaltyExpiryWarningResponse(total, earliest);
    }

    @Override
    @Transactional
    public LoyaltyReferralResponse getReferralInfo(UUID userId, UUID companyId) {
        LoyaltyAccount account = getOrCreateAccount(userId, companyId);
        String code = ensureReferralCode(account, companyId);
        // total = people who applied the code; converted = those who completed a first purchase
        long total = accountRepository.countByReferredByCodeAndCompanyId(code, companyId);
        long converted = referralConversionRepository.countByReferrerAccountIdAndCompanyId(account.getId(), companyId);
        long pointsEarned = referralConversionRepository.sumPointsAwardedByReferrerAccountId(account.getId(), companyId);
        return new LoyaltyReferralResponse(code, total, converted, pointsEarned);
    }

    @Override
    @Transactional
    public void applyReferralCode(UUID userId, UUID companyId, String code) {
        LoyaltyAccount account = getOrCreateAccount(userId, companyId);

        // Ensure this user has a referral code and check they're not applying their own
        String ownCode = ensureReferralCode(account, companyId);
        if (ownCode.equalsIgnoreCase(code.trim())) {
            throw new BadRequestException("You cannot apply your own referral code");
        }
        if (account.getReferredByCode() != null) {
            throw new ConflictException("A referral code has already been applied to your account");
        }
        if (account.isReferralConverted()) {
            throw new ConflictException("Your account has already completed a referral conversion");
        }

        // Verify the code belongs to a real account in this company
        accountRepository.findByReferralCodeAndCompanyId(code.toUpperCase(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Referral code not found"));

        int updated = accountRepository.setReferredByCodeIfAbsent(account.getId(), code.toUpperCase());
        if (updated == 0) {
            throw new ConflictException("A referral code has already been applied to your account");
        }
        log.info("[LOYALTY] User {} applied referral code {} in company {}", userId, code, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyRedemptionQuoteResponse getRedemptionQuote(UUID userId, UUID companyId, int pointsToRedeem) {
        LoyaltyAccount account = accountRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElse(null);
        long balance = account != null ? account.getPointsBalance() : 0L;

        LoyaltyPolicy policy = policyRepository.findFirstByCompanyIdAndActiveTrue(companyId).orElse(null);
        if (policy == null) {
            return new LoyaltyRedemptionQuoteResponse(userId, companyId, pointsToRedeem, 0, balance, balance, false, "No active loyalty program");
        }
        if (pointsToRedeem < policy.getMinRedemptionPoints()) {
            return new LoyaltyRedemptionQuoteResponse(userId, companyId, pointsToRedeem, 0, balance, balance, false,
                    "Minimum redemption is " + policy.getMinRedemptionPoints() + " points");
        }
        if (pointsToRedeem > balance) {
            return new LoyaltyRedemptionQuoteResponse(userId, companyId, pointsToRedeem, 0, balance, balance, false, "Insufficient points balance");
        }

        long discountCents = (long) pointsToRedeem * policy.getPointValueCents();
        return new LoyaltyRedemptionQuoteResponse(userId, companyId, pointsToRedeem, discountCents,
                balance, balance - pointsToRedeem, true, null);
    }

    // -------------------------------------------------------------------------
    // Order integration
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public long applyRedemption(UUID userId, UUID companyId, UUID orderId, int pointsToRedeem) {
        if (pointsToRedeem <= 0) return 0L;

        LoyaltyPolicy policy = policyRepository.findFirstByCompanyIdAndActiveTrue(companyId)
                .orElseThrow(() -> new BadRequestException("No active loyalty program for this store"));

        if (pointsToRedeem < policy.getMinRedemptionPoints()) {
            throw new BadRequestException("Minimum redemption is " + policy.getMinRedemptionPoints() + " points");
        }

        LoyaltyAccount account = accountRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new BadRequestException("No loyalty account found"));

        int updated = accountRepository.deductPoints(account.getId(), pointsToRedeem);
        if (updated == 0) {
            throw new ConflictException("Insufficient loyalty points balance");
        }

        long discountCents = (long) pointsToRedeem * policy.getPointValueCents();

        LoyaltyTransaction tx = new LoyaltyTransaction();
        tx.setAccount(account);
        tx.setUserId(userId);
        tx.setCompanyId(companyId);
        tx.setType(LoyaltyTransactionType.REDEEM_ORDER);
        tx.setPointsDelta(-pointsToRedeem);
        tx.setValueCents(-discountCents);
        tx.setSourceOrderId(orderId);
        tx.setReason("Redeemed for order #" + orderId);
        transactionRepository.save(tx);

        log.info("[LOYALTY] User {} redeemed {} points (${} discount) on order {}", userId, pointsToRedeem, discountCents / 100.0, orderId);
        return discountCents;
    }

    @Override
    @Transactional
    public void recordOrderEarn(Order order, UUID companyId) {
        LoyaltyPolicy policy = policyRepository.findFirstByCompanyIdAndActiveTrue(companyId).orElse(null);
        if (policy == null) return;

        UUID userId = order.getUser().getId();
        LoyaltyAccount account = getOrCreateAccount(userId, companyId);

        // Resolve tier multiplier
        BigDecimal multiplier = resolveMultiplier(account);

        boolean earnPoints = policy.getEarnMode() == LoyaltyEarnMode.POINTS || policy.getEarnMode() == LoyaltyEarnMode.BOTH;
        boolean earnCashback = policy.getEarnMode() == LoyaltyEarnMode.CASHBACK || policy.getEarnMode() == LoyaltyEarnMode.BOTH;

        long earnedPoints = 0;
        if (earnPoints) {
            BigDecimal orderDollars = order.getTotalAmount();
            earnedPoints = orderDollars
                    .multiply(policy.getEarnRatePerDollar())
                    .multiply(multiplier)
                    .setScale(0, RoundingMode.FLOOR)
                    .longValue();

            if (earnedPoints > 0) {
                accountRepository.addPoints(account.getId(), earnedPoints);

                Instant expiresAt = policy.getPointsExpiryDays() != null
                        ? Instant.now().plus(policy.getPointsExpiryDays(), ChronoUnit.DAYS)
                        : null;

                LoyaltyTransaction tx = new LoyaltyTransaction();
                tx.setAccount(account);
                tx.setUserId(userId);
                tx.setCompanyId(companyId);
                tx.setType(LoyaltyTransactionType.EARN_ORDER);
                tx.setPointsDelta(earnedPoints);
                tx.setValueCents(earnedPoints * policy.getPointValueCents());
                tx.setSourceOrderId(order.getId());
                tx.setExpiresAt(expiresAt);
                tx.setReason("Earned from order #" + order.getId());
                transactionRepository.save(tx);

                // Refresh account to get updated lifetimePoints for tier evaluation
                account = accountRepository.findByUserIdAndCompanyId(userId, companyId).orElse(account);
                evaluateTierPromotion(account, companyId);

                log.info("[LOYALTY] User {} earned {} points from order {}", userId, earnedPoints, order.getId());
            }
        }

        // ── Streak tracking ──────────────────────────────────────────────────
        String currentMonth = YearMonth.now().toString();
        String lastMonth = account.getLastOrderYearMonth();
        int streak = account.getCurrentStreak();
        if (lastMonth == null) {
            streak = 1;
        } else if (YearMonth.parse(lastMonth).plusMonths(1).toString().equals(currentMonth)) {
            streak++;
        } else if (!lastMonth.equals(currentMonth)) {
            streak = 1;
        }
        // same month → streak unchanged
        accountRepository.updateStreak(account.getId(), streak, currentMonth);

        if (policy.getStreakBonusPoints() > 0 && policy.getStreakBonusThreshold() > 0
                && streak % policy.getStreakBonusThreshold() == 0
                && !transactionRepository.existsBySourceOrderIdAndType(order.getId(), LoyaltyTransactionType.EARN_STREAK)) {
            accountRepository.addPoints(account.getId(), policy.getStreakBonusPoints());
            LoyaltyTransaction streakTx = new LoyaltyTransaction();
            streakTx.setAccount(account);
            streakTx.setUserId(userId);
            streakTx.setCompanyId(companyId);
            streakTx.setType(LoyaltyTransactionType.EARN_STREAK);
            streakTx.setSourceOrderId(order.getId());
            streakTx.setPointsDelta(policy.getStreakBonusPoints());
            streakTx.setValueCents((long) policy.getStreakBonusPoints() * policy.getPointValueCents());
            streakTx.setReason(streak + "-month purchase streak bonus");
            transactionRepository.save(streakTx);
            log.info("[LOYALTY] User {} earned {} streak bonus points (streak={})", userId, policy.getStreakBonusPoints(), streak);
        }

        // ── Referral conversion (first order only) ───────────────────────────
        // markReferralConverted is an atomic compare-and-set (returns 1 only if not yet converted).
        // We claim the slot first to prevent two concurrent first-order events both awarding the bonus.
        final LoyaltyAccount finalAccount = account;
        long orderEarnCount = transactionRepository.countByAccountIdAndType(account.getId(), LoyaltyTransactionType.EARN_ORDER);
        if (orderEarnCount == 1 && account.getReferredByCode() != null && !account.isReferralConverted()) {
            int claimed = accountRepository.markReferralConverted(finalAccount.getId());
            if (claimed > 0) {
                accountRepository.findByReferralCodeAndCompanyId(account.getReferredByCode(), companyId)
                        .ifPresent(referrerAccount -> {
                            int bonus = policy.getReferralBonusPoints();
                            if (bonus > 0) {
                                accountRepository.addPoints(referrerAccount.getId(), bonus);
                                LoyaltyTransaction refTx = new LoyaltyTransaction();
                                refTx.setAccount(referrerAccount);
                                refTx.setUserId(referrerAccount.getUserId());
                                refTx.setCompanyId(companyId);
                                refTx.setType(LoyaltyTransactionType.EARN_REFERRAL);
                                refTx.setPointsDelta(bonus);
                                refTx.setValueCents((long) bonus * policy.getPointValueCents());
                                refTx.setSourceOrderId(order.getId());
                                refTx.setReason("Referral bonus — " + finalAccount.getUserId() + " completed first order");
                                transactionRepository.save(refTx);
                            }
                            ReferralConversion conversion = new ReferralConversion();
                            conversion.setReferrerAccountId(referrerAccount.getId());
                            conversion.setReferredAccountId(finalAccount.getId());
                            conversion.setCompanyId(companyId);
                            conversion.setOrderId(order.getId());
                            conversion.setPointsAwarded(bonus);
                            referralConversionRepository.save(conversion);
                            log.info("[LOYALTY] Referral conversion: referrer={} referred={} bonus={}", referrerAccount.getId(), finalAccount.getId(), bonus);
                        });
            }
        }

        // ── Kafka: publish PointsEarned ──────────────────────────────────────
        if (earnedPoints > 0) {
            final long finalEarned = earnedPoints;
            accountRepository.findByUserIdAndCompanyId(userId, companyId).ifPresent(refreshed ->
                    loyaltyEventPublisher.publish(new LoyaltyEvent.PointsEarned(
                            userId, companyId, finalEarned,
                            LoyaltyTransactionType.EARN_ORDER.name(),
                            refreshed.getPointsBalance()))
            );
        }

        if (earnCashback && policy.getCashbackRatePercent().compareTo(BigDecimal.ZERO) > 0) {
            long cashbackCents = order.getTotalAmount()
                    .multiply(policy.getCashbackRatePercent())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                    .longValue();

            if (cashbackCents > 0) {
                try {
                    customerCreditService.issueCredit(
                            userId,
                            new IssueCreditRequest(cashbackCents, CreditEntryType.LOYALTY_CASHBACK,
                                    "Cashback from order #" + order.getId(), null),
                            userId, null, null);
                    log.info("[LOYALTY] User {} earned {} cents cashback from order {}", userId, cashbackCents, order.getId());
                } catch (Exception e) {
                    log.error("[LOYALTY] Failed to issue cashback credit for user {} order {}: {}", userId, order.getId(), e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional
    public void clawbackEarnedPoints(UUID orderId, long refundedAmountCents, long orderTotalCents) {
        if (refundedAmountCents <= 0 || orderTotalCents <= 0) return;

        LoyaltyTransaction earnTx = transactionRepository
                .findFirstBySourceOrderIdAndType(orderId, LoyaltyTransactionType.EARN_ORDER)
                .orElse(null);
        if (earnTx == null) return;  // Order never earned points (no policy, or 0 earn rate)

        long earnedPoints = earnTx.getPointsDelta();
        if (earnedPoints <= 0) return;

        // Proportional target: earnedPoints * refunded/total. Cap refund at 100%
        // of the order to guard against odd partial-refund-exceeds-total inputs.
        long effectiveRefund = Math.min(refundedAmountCents, orderTotalCents);
        long shouldHaveReversed = BigDecimal.valueOf(earnedPoints)
                .multiply(BigDecimal.valueOf(effectiveRefund))
                .divide(BigDecimal.valueOf(orderTotalCents), 0, RoundingMode.HALF_UP)
                .longValue();

        long alreadyReversed = transactionRepository.sumAbsPointsForOrderAndType(
                orderId, LoyaltyTransactionType.EARN_REVERSAL);
        long deltaToReverse = shouldHaveReversed - alreadyReversed;
        if (deltaToReverse <= 0) return;
        // Never reverse more than was originally earned.
        deltaToReverse = Math.min(deltaToReverse, earnedPoints - alreadyReversed);
        if (deltaToReverse <= 0) return;

        // Best-effort balance deduction. If the customer has already spent the points
        // (balance < deltaToReverse), the atomic guard inside deductPoints returns 0 and
        // the balance stays at whatever it is. We STILL record the reversal so the audit
        // trail captures intent — the resulting balance won't be made negative, and the
        // shortfall is a known operational risk of refunding orders whose points have
        // already been spent.
        accountRepository.deductPoints(earnTx.getAccount().getId(), deltaToReverse);

        long perPointValueCents = earnedPoints > 0
                ? earnTx.getValueCents() / earnedPoints
                : 0;

        LoyaltyTransaction reversal = new LoyaltyTransaction();
        reversal.setAccount(earnTx.getAccount());
        reversal.setUserId(earnTx.getUserId());
        reversal.setCompanyId(earnTx.getCompanyId());
        reversal.setType(LoyaltyTransactionType.EARN_REVERSAL);
        reversal.setPointsDelta(-deltaToReverse);
        reversal.setValueCents(-(deltaToReverse * perPointValueCents));
        reversal.setSourceOrderId(orderId);
        reversal.setReason("Reversed: refund of " + effectiveRefund + " cents on order #" + orderId);
        transactionRepository.save(reversal);

        log.info("[LOYALTY] Clawed back {} points (of {} originally earned) for order {} after refund of {} / {} cents",
                deltaToReverse, earnedPoints, orderId, effectiveRefund, orderTotalCents);
    }

    @Override
    @Transactional
    public void restoreRedeemedPoints(UUID orderId) {
        if (transactionRepository.existsBySourceOrderIdAndType(orderId, LoyaltyTransactionType.RESTORE_ORDER)) {
            log.debug("[LOYALTY] Restore already recorded for order {} — skipping", orderId);
            return;
        }
        transactionRepository.findFirstBySourceOrderIdAndType(orderId, LoyaltyTransactionType.REDEEM_ORDER)
                .ifPresent(redemptionTx -> {
                    long delta = Math.abs(redemptionTx.getPointsDelta());
                    accountRepository.addToBalance(redemptionTx.getAccount().getId(), delta);

                    LoyaltyTransaction restore = new LoyaltyTransaction();
                    restore.setAccount(redemptionTx.getAccount());
                    restore.setUserId(redemptionTx.getUserId());
                    restore.setCompanyId(redemptionTx.getCompanyId());
                    restore.setType(LoyaltyTransactionType.RESTORE_ORDER);
                    restore.setPointsDelta(delta);
                    restore.setValueCents(Math.abs(redemptionTx.getValueCents()));
                    restore.setSourceOrderId(orderId);
                    restore.setReason("Restored: order #" + orderId + " failed/cancelled");
                    transactionRepository.save(restore);

                    log.info("[LOYALTY] Restored {} points for user {} from cancelled order {}", delta, redemptionTx.getUserId(), orderId);
                });
    }

    // -------------------------------------------------------------------------
    // Operator actions
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public LoyaltyTransactionResponse issueBonus(UUID companyId, UUID ownerId, IssueBonusRequest request) {
        assertOwner(companyId, ownerId);

        LoyaltyAccount account = getOrCreateAccount(request.getUserId(), companyId);
        accountRepository.addPoints(account.getId(), request.getPoints());

        LoyaltyTransaction tx = new LoyaltyTransaction();
        tx.setAccount(account);
        tx.setUserId(request.getUserId());
        tx.setCompanyId(companyId);
        tx.setType(LoyaltyTransactionType.EARN_BONUS);
        tx.setPointsDelta(request.getPoints());
        tx.setValueCents((long) request.getPoints() * getPointValueCents(companyId));
        tx.setReason(request.getReason() != null ? request.getReason() : "Operator bonus");
        LoyaltyTransaction saved = transactionRepository.save(tx);

        account = accountRepository.findByUserIdAndCompanyId(request.getUserId(), companyId).orElse(account);
        evaluateTierPromotion(account, companyId);

        log.info("[LOYALTY] Operator issued {} bonus points to user {} in company {}", request.getPoints(), request.getUserId(), companyId);
        return toTransactionResponse(saved);
    }

    @Override
    @Transactional
    public LoyaltyTransactionResponse adjustPoints(UUID accountId, UUID companyId, UUID ownerId, AdjustPointsRequest request) {
        assertOwner(companyId, ownerId);

        LoyaltyAccount account = accountRepository.findByIdAndCompanyId(accountId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Loyalty account not found"));

        int delta = request.getPointsDelta();
        if (delta > 0) {
            accountRepository.addToBalance(accountId, delta);
        } else if (delta < 0) {
            int updated = accountRepository.deductPoints(accountId, Math.abs(delta));
            if (updated == 0) {
                throw new BadRequestException("Insufficient points balance for this adjustment");
            }
        }

        LoyaltyTransaction tx = new LoyaltyTransaction();
        tx.setAccount(account);
        tx.setUserId(account.getUserId());
        tx.setCompanyId(companyId);
        tx.setType(LoyaltyTransactionType.ADJUST);
        tx.setPointsDelta(delta);
        tx.setValueCents((long) Math.abs(delta) * getPointValueCents(companyId) * (delta >= 0 ? 1 : -1));
        tx.setReason(request.getReason() != null ? request.getReason() : "Manual operator adjustment");
        return toTransactionResponse(transactionRepository.save(tx));
    }

    // -------------------------------------------------------------------------
    // Policy & tier management
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public LoyaltyPolicyResponse createOrUpdatePolicy(UUID companyId, UUID ownerId, CreateLoyaltyPolicyRequest request) {
        assertOwner(companyId, ownerId);

        LoyaltyEarnMode mode;
        try {
            mode = LoyaltyEarnMode.valueOf(request.getEarnMode());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid earnMode: " + request.getEarnMode());
        }

        // Deactivate existing active policies before creating/updating
        policyRepository.findFirstByCompanyIdAndActiveTrue(companyId).ifPresent(existing -> {
            existing.setActive(false);
            policyRepository.save(existing);
        });

        LoyaltyPolicy policy = new LoyaltyPolicy();
        policy.setCompanyId(companyId);
        policy.setName(request.getName());
        policy.setEarnRatePerDollar(request.getEarnRatePerDollar());
        policy.setPointValueCents(request.getPointValueCents());
        policy.setMinRedemptionPoints(request.getMinRedemptionPoints());
        policy.setPointsExpiryDays(request.getPointsExpiryDays());
        policy.setBirthdayBonusPoints(request.getBirthdayBonusPoints());
        policy.setBirthdayBonusCreditCents(request.getBirthdayBonusCreditCents());
        policy.setCashbackRatePercent(request.getCashbackRatePercent());
        policy.setEarnMode(mode);
        policy.setActive(true);
        policy.setReferralBonusPoints(request.getReferralBonusPoints());
        policy.setStreakBonusThreshold(request.getStreakBonusThreshold());
        policy.setStreakBonusPoints(request.getStreakBonusPoints());
        return toPolicyResponse(policyRepository.save(policy));
    }

    @Override
    @Transactional(readOnly = true)
    public LoyaltyPolicyResponse getPolicy(UUID companyId) {
        return policyRepository.findFirstByCompanyIdAndActiveTrue(companyId)
                .map(this::toPolicyResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No active loyalty policy for this company"));
    }

    @Override
    @Transactional
    public LoyaltyTierResponse createTier(UUID companyId, UUID ownerId, CreateLoyaltyTierRequest request) {
        assertOwner(companyId, ownerId);
        LoyaltyTier tier = new LoyaltyTier();
        tier.setCompanyId(companyId);
        tier.setName(request.getName());
        tier.setMinPoints(request.getMinPoints());
        tier.setEarnMultiplier(request.getEarnMultiplier());
        tier.setPerksJson(request.getPerksJson());
        tier.setBadgeColor(request.getBadgeColor());
        tier.setDisplayOrder(request.getDisplayOrder());
        return toTierResponse(tierRepository.save(tier));
    }

    @Override
    @Transactional
    public LoyaltyTierResponse updateTier(UUID tierId, UUID companyId, UUID ownerId, CreateLoyaltyTierRequest request) {
        assertOwner(companyId, ownerId);
        LoyaltyTier tier = tierRepository.findById(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Loyalty tier not found"));
        if (!tier.getCompanyId().equals(companyId)) {
            throw new ForbiddenException("Tier does not belong to this company");
        }
        tier.setName(request.getName());
        tier.setMinPoints(request.getMinPoints());
        tier.setEarnMultiplier(request.getEarnMultiplier());
        tier.setPerksJson(request.getPerksJson());
        tier.setBadgeColor(request.getBadgeColor());
        tier.setDisplayOrder(request.getDisplayOrder());
        return toTierResponse(tierRepository.save(tier));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoyaltyTierResponse> listTiers(UUID companyId) {
        return tierRepository.findByCompanyIdOrderByMinPointsAsc(companyId).stream()
                .map(this::toTierResponse)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Package-visible: called by LoyaltyScheduler
    // -------------------------------------------------------------------------

    public List<UUID> findAccountIdsWithExpiredPoints() {
        return transactionRepository.findAccountIdsWithExpiredPoints(Instant.now());
    }

    @Transactional
    public void expireAccountPoints(LoyaltyAccount account) {
        List<LoyaltyTransaction> earns = transactionRepository.findExpiredEarns(account.getId(), Instant.now());
        if (earns.isEmpty()) return;

        long totalToExpire = 0;
        for (LoyaltyTransaction earn : earns) {
            if (transactionRepository.claimForExpiry(earn.getId()) == 0) {
                continue; // concurrent scheduler run already claimed this row
            }
            totalToExpire += earn.getPointsDelta();
        }

        if (totalToExpire <= 0) return;

        long deduction = Math.min(totalToExpire, account.getPointsBalance());
        if (deduction <= 0) return;

        accountRepository.deductPoints(account.getId(), deduction);

        LoyaltyTransaction expireTx = new LoyaltyTransaction();
        expireTx.setAccount(account);
        expireTx.setUserId(account.getUserId());
        expireTx.setCompanyId(account.getCompanyId());
        expireTx.setType(LoyaltyTransactionType.EXPIRE);
        expireTx.setPointsDelta(-deduction);
        expireTx.setReason("Points expired per policy");
        transactionRepository.save(expireTx);

        log.info("[LOYALTY] Expired {} points for account {} (user {})", deduction, account.getId(), account.getUserId());
    }

    @Transactional
    public void issueBirthdayReward(LoyaltyAccount account, LoyaltyPolicy policy) {
        int year = java.time.LocalDate.now().getYear();
        // Atomic claim — if another scheduler run already claimed this year's slot, the UPDATE
        // returns 0 rows and we bail. This replaces the old SELECT-then-INSERT pattern which
        // allowed two concurrent runs to both see "no reward yet" and both issue the reward.
        if (accountRepository.claimBirthdayRewardForYear(account.getId(), year) == 0) return;

        if (policy.getBirthdayBonusPoints() > 0) {
            accountRepository.addPoints(account.getId(), policy.getBirthdayBonusPoints());

            Instant expiresAt = policy.getPointsExpiryDays() != null
                    ? Instant.now().plus(policy.getPointsExpiryDays(), ChronoUnit.DAYS)
                    : null;

            LoyaltyTransaction tx = new LoyaltyTransaction();
            tx.setAccount(account);
            tx.setUserId(account.getUserId());
            tx.setCompanyId(account.getCompanyId());
            tx.setType(LoyaltyTransactionType.EARN_BIRTHDAY);
            tx.setPointsDelta(policy.getBirthdayBonusPoints());
            tx.setValueCents((long) policy.getBirthdayBonusPoints() * policy.getPointValueCents());
            tx.setReason("Birthday reward");
            tx.setExpiresAt(expiresAt);
            transactionRepository.save(tx);

            log.info("[LOYALTY] Issued {} birthday points to account {} (user {})", policy.getBirthdayBonusPoints(), account.getId(), account.getUserId());
        }

        if (policy.getBirthdayBonusCreditCents() > 0) {
            try {
                customerCreditService.issueCredit(
                        account.getUserId(),
                        new IssueCreditRequest((long) policy.getBirthdayBonusCreditCents(),
                                CreditEntryType.LOYALTY_CASHBACK, "Birthday store credit", null),
                        account.getUserId(), null, null);
                log.info("[LOYALTY] Issued {} cents birthday credit to user {}", policy.getBirthdayBonusCreditCents(), account.getUserId());
            } catch (Exception e) {
                log.error("[LOYALTY] Failed to issue birthday credit to user {}: {}", account.getUserId(), e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private LoyaltyAccount getOrCreateAccount(UUID userId, UUID companyId) {
        return accountRepository.findByUserIdAndCompanyId(userId, companyId).orElseGet(() -> {
            LoyaltyAccount a = new LoyaltyAccount();
            a.setUserId(userId);
            a.setCompanyId(companyId);
            return accountRepository.save(a);
        });
    }

    private LoyaltyAccount emptyAccount(UUID userId, UUID companyId) {
        LoyaltyAccount a = new LoyaltyAccount();
        a.setUserId(userId);
        a.setCompanyId(companyId);
        return a;
    }

    /**
     * Returns the account's referral code, generating and persisting one lazily if absent.
     * Uses a retry loop with the atomic setReferralCodeIfAbsent guard to handle concurrent requests.
     */
    private String ensureReferralCode(LoyaltyAccount account, UUID companyId) {
        if (account.getReferralCode() != null) return account.getReferralCode();
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = generateReferralCode();
            // Check uniqueness within company before trying to persist
            if (accountRepository.findByReferralCodeAndCompanyId(candidate, companyId).isPresent()) {
                continue;
            }
            int set = accountRepository.setReferralCodeIfAbsent(account.getId(), candidate);
            if (set > 0) return candidate;
            // Another thread beat us — re-read and return their code
            return accountRepository.findById(account.getId())
                    .map(LoyaltyAccount::getReferralCode)
                    .orElse(candidate);
        }
        throw new IllegalStateException("Failed to generate unique referral code after 10 attempts");
    }

    private static String generateReferralCode() {
        StringBuilder sb = new StringBuilder(REFERRAL_CODE_LENGTH);
        for (int i = 0; i < REFERRAL_CODE_LENGTH; i++) {
            sb.append(REFERRAL_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(REFERRAL_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private void evaluateTierPromotion(LoyaltyAccount account, UUID companyId) {
        List<LoyaltyTier> tiers = tierRepository.findByCompanyIdOrderByMinPointsDesc(
                companyId, org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
        UUID newTierId = null;
        String newTierName = null;
        for (LoyaltyTier tier : tiers) {
            if (account.getLifetimePoints() >= tier.getMinPoints()) {
                newTierId = tier.getId();
                newTierName = tier.getName();
                break;
            }
        }
        if (!java.util.Objects.equals(account.getCurrentTierId(), newTierId)) {
            int updated = accountRepository.updateTierIfChanged(account.getId(), newTierId, Instant.now());
            if (updated > 0) {
                log.info("[LOYALTY] Account {} tier updated to {}", account.getId(), newTierId);
                String previousTierName = account.getCurrentTierId() != null
                        ? tierRepository.findById(account.getCurrentTierId()).map(LoyaltyTier::getName).orElse(null)
                        : null;
                loyaltyEventPublisher.publish(new LoyaltyEvent.TierUpgraded(
                        account.getUserId(), companyId, newTierName, previousTierName));
            }
        }
    }

    private BigDecimal resolveMultiplier(LoyaltyAccount account) {
        if (account.getCurrentTierId() == null) return BigDecimal.ONE;
        return tierRepository.findById(account.getCurrentTierId())
                .map(LoyaltyTier::getEarnMultiplier)
                .orElse(BigDecimal.ONE);
    }

    private int getPointValueCents(UUID companyId) {
        return policyRepository.findFirstByCompanyIdAndActiveTrue(companyId)
                .map(LoyaltyPolicy::getPointValueCents)
                .orElse(1);
    }

    private void assertOwner(UUID companyId, UUID userId) {
        companyAccessService.require(companyId, userId, CompanyCapability.MANAGE_PROMOTIONS);
    }

    private LoyaltyAccountResponse toAccountResponse(LoyaltyAccount a) {
        String tierName = null;
        if (a.getCurrentTierId() != null) {
            tierName = tierRepository.findById(a.getCurrentTierId())
                    .map(LoyaltyTier::getName).orElse(null);
        }
        return new LoyaltyAccountResponse(
                a.getId(),
                a.getUserId(), a.getCompanyId(),
                a.getPointsBalance(), a.getLifetimePoints(),
                a.getCurrentTierId(), tierName, a.getTierUpdatedAt(),
                a.getReferralCode(), a.getCurrentStreak(),
                a.getCreatedAt(), a.getUpdatedAt());
    }

    private LoyaltyTransactionResponse toTransactionResponse(LoyaltyTransaction t) {
        return new LoyaltyTransactionResponse(
                t.getId(), t.getAccount().getId(), t.getUserId(), t.getCompanyId(),
                t.getType().name(), t.getPointsDelta(), t.getValueCents(),
                t.getSourceOrderId(), t.getExpiresAt(), t.getReason(), t.getCreatedAt());
    }

    private LoyaltyPolicyResponse toPolicyResponse(LoyaltyPolicy p) {
        return new LoyaltyPolicyResponse(
                p.getId(), p.getCompanyId(), p.getName(),
                p.getEarnRatePerDollar(), p.getPointValueCents(), p.getMinRedemptionPoints(),
                p.getPointsExpiryDays(), p.getBirthdayBonusPoints(), p.getBirthdayBonusCreditCents(),
                p.getCashbackRatePercent(), p.getEarnMode().name(), p.isActive(),
                p.getReferralBonusPoints(), p.getStreakBonusThreshold(), p.getStreakBonusPoints(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private LoyaltyTierResponse toTierResponse(LoyaltyTier t) {
        return new LoyaltyTierResponse(
                t.getId(), t.getCompanyId(), t.getName(), t.getMinPoints(),
                t.getEarnMultiplier(), t.getPerksJson(), t.getBadgeColor(),
                t.getDisplayOrder(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
