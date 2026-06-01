package backend.services.impl.promotions;

import backend.events.loyalty.LoyaltyEvent;
import backend.models.core.LoyaltyAccount;
import backend.models.core.LoyaltyPolicy;
import backend.models.core.LoyaltyTransaction;
import backend.repositories.LoyaltyAccountRepository;
import backend.repositories.LoyaltyPolicyRepository;
import backend.repositories.LoyaltyTransactionRepository;
import backend.repositories.UserRepository;
import backend.services.intf.promotions.LoyaltyEventPublisher;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltySchedulerTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_1     = TestIds.uuid(2);
    private static final UUID USER_2     = TestIds.uuid(3);
    private static final UUID ACCOUNT_ID = TestIds.uuid(4);

    private LoyaltyPolicyRepository      policyRepository;
    private LoyaltyAccountRepository     accountRepository;
    private LoyaltyTransactionRepository transactionRepository;
    private UserRepository               userRepository;
    private LoyaltyServiceImpl           loyaltyService;
    private LoyaltyEventPublisher        loyaltyEventPublisher;

    private LoyaltyScheduler scheduler;

    @BeforeEach
    void setUp() {
        policyRepository      = mock(LoyaltyPolicyRepository.class);
        accountRepository     = mock(LoyaltyAccountRepository.class);
        transactionRepository = mock(LoyaltyTransactionRepository.class);
        userRepository        = mock(UserRepository.class);
        loyaltyService        = mock(LoyaltyServiceImpl.class);
        loyaltyEventPublisher = mock(LoyaltyEventPublisher.class);

        scheduler = new LoyaltyScheduler(
                policyRepository, accountRepository, transactionRepository,
                userRepository, loyaltyService, loyaltyEventPublisher);
    }

    // ─── processBirthdayRewards ───────────────────────────────────────────────

    @Test
    void processBirthdayRewards_noPolicies_doesNotCheckUsers() {
        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of());

        scheduler.processBirthdayRewards();

        verify(userRepository, never()).findUserIdsWithBirthday(anyInt(), anyInt());
    }

    @Test
    void processBirthdayRewards_policyWithZeroBonuses_skipsPolicy() {
        LoyaltyPolicy policy = policy(0, 0);
        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy));

        scheduler.processBirthdayRewards();

        verify(userRepository, never()).findUserIdsWithBirthday(anyInt(), anyInt());
    }

    @Test
    void processBirthdayRewards_userHasNoAccount_skipsUser() {
        LoyaltyPolicy policy = policy(100, 0);
        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy));
        when(userRepository.findUserIdsWithBirthday(anyInt(), anyInt())).thenReturn(List.of(USER_1));
        when(accountRepository.findByUserIdAndCompanyId(USER_1, COMPANY_ID)).thenReturn(Optional.empty());

        scheduler.processBirthdayRewards();

        verify(loyaltyService, never()).issueBirthdayReward(any(), any());
    }

    @Test
    void processBirthdayRewards_userHasAccount_issuesBirthdayReward() {
        LoyaltyPolicy policy = policy(100, 0);
        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy));
        when(userRepository.findUserIdsWithBirthday(anyInt(), anyInt())).thenReturn(List.of(USER_1));
        LoyaltyAccount account = account(USER_1);
        when(accountRepository.findByUserIdAndCompanyId(USER_1, COMPANY_ID)).thenReturn(Optional.of(account));

        scheduler.processBirthdayRewards();

        verify(loyaltyService).issueBirthdayReward(account, policy);
    }

    @Test
    void processBirthdayRewards_oneUserThrows_otherUserStillProcessed() {
        LoyaltyPolicy policy = policy(100, 0);
        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy));
        when(userRepository.findUserIdsWithBirthday(anyInt(), anyInt())).thenReturn(List.of(USER_1, USER_2));

        LoyaltyAccount account1 = account(USER_1);
        LoyaltyAccount account2 = account(USER_2);
        when(accountRepository.findByUserIdAndCompanyId(USER_1, COMPANY_ID)).thenReturn(Optional.of(account1));
        when(accountRepository.findByUserIdAndCompanyId(USER_2, COMPANY_ID)).thenReturn(Optional.of(account2));
        doThrow(new RuntimeException("DB error")).when(loyaltyService).issueBirthdayReward(account1, policy);

        scheduler.processBirthdayRewards(); // must not throw

        verify(loyaltyService).issueBirthdayReward(account2, policy);
    }

    @Test
    void processBirthdayRewards_policyThrows_otherPoliciesStillProcessed() {
        LoyaltyPolicy policy1 = policy(100, 0);
        LoyaltyPolicy policy2 = policy(50, 0);
        policy2.setId(TestIds.uuid(99));
        policy2.setCompanyId(TestIds.uuid(98));

        when(policyRepository.findAllByActiveTrue()).thenReturn(List.of(policy1, policy2));

        // policy1 — findUserIdsWithBirthday returns list but findByUserIdAndCompanyId throws
        when(userRepository.findUserIdsWithBirthday(anyInt(), anyInt())).thenReturn(List.of(USER_1));
        when(accountRepository.findByUserIdAndCompanyId(eq(USER_1), eq(COMPANY_ID)))
                .thenThrow(new RuntimeException("DB outage for company 1"));
        when(accountRepository.findByUserIdAndCompanyId(eq(USER_1), eq(TestIds.uuid(98))))
                .thenReturn(Optional.empty());

        scheduler.processBirthdayRewards(); // must not throw; both policies attempted
    }

    // ─── publishExpiryWarnings ────────────────────────────────────────────────

    @Test
    void publishExpiryWarnings_noExpiringAccounts_publisherNotCalled() {
        when(transactionRepository.findAccountIdsWithPointsExpiringSoon(any(), any())).thenReturn(List.of());

        scheduler.publishExpiryWarnings();

        verify(loyaltyEventPublisher, never()).publish(any());
    }

    @Test
    void publishExpiryWarnings_accountNotFound_skipsAccount() {
        when(transactionRepository.findAccountIdsWithPointsExpiringSoon(any(), any()))
                .thenReturn(List.of(ACCOUNT_ID));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        scheduler.publishExpiryWarnings();

        verify(loyaltyEventPublisher, never()).publish(any());
    }

    @Test
    void publishExpiryWarnings_totalExpiringPositive_publishesEvent() {
        when(transactionRepository.findAccountIdsWithPointsExpiringSoon(any(), any()))
                .thenReturn(List.of(ACCOUNT_ID));
        LoyaltyAccount account = account(USER_1);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        LoyaltyTransaction txn = new LoyaltyTransaction();
        txn.setPointsDelta(200L);
        txn.setExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));
        when(transactionRepository.findPointsExpiringSoon(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of(txn));

        scheduler.publishExpiryWarnings();

        verify(loyaltyEventPublisher).publish(any(LoyaltyEvent.PointsExpiringSoon.class));
    }

    @Test
    void publishExpiryWarnings_totalExpiringZero_noEventPublished() {
        when(transactionRepository.findAccountIdsWithPointsExpiringSoon(any(), any()))
                .thenReturn(List.of(ACCOUNT_ID));
        LoyaltyAccount account = account(USER_1);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        LoyaltyTransaction txn = new LoyaltyTransaction();
        txn.setPointsDelta(0L);
        txn.setExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));
        when(transactionRepository.findPointsExpiringSoon(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of(txn));

        scheduler.publishExpiryWarnings();

        verify(loyaltyEventPublisher, never()).publish(any());
    }

    @Test
    void publishExpiryWarnings_perAccountException_othersStillProcessed() {
        UUID accountId2 = TestIds.uuid(50);
        when(transactionRepository.findAccountIdsWithPointsExpiringSoon(any(), any()))
                .thenReturn(List.of(ACCOUNT_ID, accountId2));
        when(accountRepository.findById(ACCOUNT_ID)).thenThrow(new RuntimeException("DB error"));
        when(accountRepository.findById(accountId2)).thenReturn(Optional.empty());

        scheduler.publishExpiryWarnings(); // must not throw
    }

    // ─── expirePoints ──────────────────────────────────────────────────────────

    @Test
    void expirePoints_zeroBalanceAccount_skipsExpiry() {
        when(loyaltyService.findAccountIdsWithExpiredPoints()).thenReturn(List.of(ACCOUNT_ID));
        LoyaltyAccount account = account(USER_1);
        account.setPointsBalance(0L);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        scheduler.expirePoints();

        verify(loyaltyService, never()).expireAccountPoints(any());
    }

    @Test
    void expirePoints_positiveBalance_callsExpireAccountPoints() {
        when(loyaltyService.findAccountIdsWithExpiredPoints()).thenReturn(List.of(ACCOUNT_ID));
        LoyaltyAccount account = account(USER_1);
        account.setPointsBalance(500L);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        scheduler.expirePoints();

        verify(loyaltyService).expireAccountPoints(account);
    }

    @Test
    void expirePoints_perAccountException_othersStillProcessed() {
        UUID accountId2 = TestIds.uuid(51);
        when(loyaltyService.findAccountIdsWithExpiredPoints()).thenReturn(List.of(ACCOUNT_ID, accountId2));

        LoyaltyAccount acct1 = account(USER_1);
        acct1.setPointsBalance(100L);
        LoyaltyAccount acct2 = account(USER_2);
        acct2.setId(accountId2);
        acct2.setPointsBalance(200L);

        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(acct1));
        when(accountRepository.findById(accountId2)).thenReturn(Optional.of(acct2));
        doThrow(new RuntimeException("DB error")).when(loyaltyService).expireAccountPoints(acct1);

        scheduler.expirePoints(); // must not throw

        verify(loyaltyService).expireAccountPoints(acct2);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private LoyaltyPolicy policy(int birthdayPoints, int birthdayCreditCents) {
        LoyaltyPolicy p = new LoyaltyPolicy();
        p.setId(TestIds.uuid(10));
        p.setCompanyId(COMPANY_ID);
        p.setBirthdayBonusPoints(birthdayPoints);
        p.setBirthdayBonusCreditCents(birthdayCreditCents);
        return p;
    }

    private LoyaltyAccount account(UUID userId) {
        LoyaltyAccount a = new LoyaltyAccount();
        a.setId(ACCOUNT_ID);
        a.setUserId(userId);
        a.setCompanyId(COMPANY_ID);
        a.setPointsBalance(100L);
        return a;
    }
}
