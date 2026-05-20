package backend.services.impl.promotions;

import backend.dtos.requests.loyalty.AdjustPointsRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyPolicyRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyTierRequest;
import backend.dtos.requests.loyalty.IssueBonusRequest;
import backend.dtos.responses.loyalty.LoyaltyAccountResponse;
import backend.dtos.responses.loyalty.LoyaltyPolicyResponse;
import backend.dtos.responses.loyalty.LoyaltyRedemptionQuoteResponse;
import backend.dtos.responses.loyalty.LoyaltyTierResponse;
import backend.dtos.responses.loyalty.LoyaltyTransactionResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.LoyaltyAccount;
import backend.models.core.LoyaltyPolicy;
import backend.models.core.LoyaltyTier;
import backend.models.core.LoyaltyTransaction;
import backend.models.core.Order;
import backend.models.core.User;
import backend.models.enums.CompanyCapability;
import backend.models.enums.LoyaltyEarnMode;
import backend.models.enums.LoyaltyTransactionType;
import backend.repositories.LoyaltyAccountRepository;
import backend.repositories.LoyaltyPolicyRepository;
import backend.repositories.LoyaltyTierRepository;
import backend.repositories.LoyaltyTransactionRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.customers.CustomerCreditService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoyaltyServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID   = TestIds.uuid(2);
    private static final UUID USER_ID    = TestIds.uuid(3);
    private static final UUID ACCOUNT_ID = TestIds.uuid(4);
    private static final UUID ORDER_ID   = TestIds.uuid(5);
    private static final UUID POLICY_ID  = TestIds.uuid(6);
    private static final UUID TIER_ID    = TestIds.uuid(7);
    private static final UUID TX_ID      = TestIds.uuid(8);

    private LoyaltyAccountRepository accountRepository;
    private LoyaltyTransactionRepository transactionRepository;
    private LoyaltyPolicyRepository policyRepository;
    private LoyaltyTierRepository tierRepository;
    private CompanyAccessService companyAccessService;
    private CustomerCreditService customerCreditService;

    private LoyaltyServiceImpl service;

    @BeforeEach
    void setUp() {
        accountRepository     = mock(LoyaltyAccountRepository.class);
        transactionRepository = mock(LoyaltyTransactionRepository.class);
        policyRepository      = mock(LoyaltyPolicyRepository.class);
        tierRepository        = mock(LoyaltyTierRepository.class);
        companyAccessService  = mock(CompanyAccessService.class);
        customerCreditService = mock(CustomerCreditService.class);

        service = new LoyaltyServiceImpl(
                accountRepository, transactionRepository, policyRepository,
                tierRepository, companyAccessService, customerCreditService);

        // Default stubs
        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any(CompanyCapability.class)))
                .thenReturn(null);
        when(transactionRepository.save(any(LoyaltyTransaction.class))).thenAnswer(inv -> {
            LoyaltyTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) tx.setId(TX_ID);
            return tx;
        });
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(inv -> {
            LoyaltyAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(ACCOUNT_ID);
            return a;
        });
        // Tier evaluation is a no-op unless overridden
        when(tierRepository.findByCompanyIdOrderByMinPointsDesc(any())).thenReturn(List.of());
    }

    // ─── getAccount ───────────────────────────────────────────────────────────

    @Test
    void getAccount_existingAccount_returnsBalance() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 500L);
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(Optional.of(account));

        LoyaltyAccountResponse result = service.getAccount(USER_ID, COMPANY_ID);

        assertEquals(500L, result.getPointsBalance());
    }

    @Test
    void getAccount_noAccount_returnsEmptyAccountWithZeroBalance() {
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(Optional.empty());

        LoyaltyAccountResponse result = service.getAccount(USER_ID, COMPANY_ID);

        assertEquals(0L, result.getPointsBalance());
    }

    // ─── getTransactions ──────────────────────────────────────────────────────

    @Test
    void getTransactions_accountFound_returnsPage() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountId(eq(ACCOUNT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getTransactions(USER_ID, COMPANY_ID, 0, 20);

        verify(transactionRepository).findByAccountId(eq(ACCOUNT_ID), any(Pageable.class));
    }

    @Test
    void getTransactions_noAccount_throwsResourceNotFound() {
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getTransactions(USER_ID, COMPANY_ID, 0, 20));
    }

    // ─── getRedemptionQuote ───────────────────────────────────────────────────

    @Test
    void getRedemptionQuote_noPolicy_returnsInvalidWithReason() {
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.empty());

        LoyaltyRedemptionQuoteResponse result = service.getRedemptionQuote(USER_ID, COMPANY_ID, 100);

        assertFalse(result.isValid());
        assertNotNull(result.getInvalidReason());
    }

    @Test
    void getRedemptionQuote_belowMinimum_returnsInvalid() {
        LoyaltyPolicy policy = makePolicy(200); // minRedemptionPoints = 200
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 500L)));

        LoyaltyRedemptionQuoteResponse result = service.getRedemptionQuote(USER_ID, COMPANY_ID, 100); // < 200

        assertFalse(result.isValid());
        assertTrue(result.getInvalidReason().contains("200"));
    }

    @Test
    void getRedemptionQuote_insufficientBalance_returnsInvalid() {
        LoyaltyPolicy policy = makePolicy(50);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 30L))); // balance 30, want 100

        LoyaltyRedemptionQuoteResponse result = service.getRedemptionQuote(USER_ID, COMPANY_ID, 100);

        assertFalse(result.isValid());
    }

    @Test
    void getRedemptionQuote_exactlyMinimum_returnsValid() {
        LoyaltyPolicy policy = makePolicy(100);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 500L)));

        LoyaltyRedemptionQuoteResponse result = service.getRedemptionQuote(USER_ID, COMPANY_ID, 100);

        assertTrue(result.isValid());
    }

    @Test
    void getRedemptionQuote_happyPath_computesDiscountCents() {
        // policy: 5 cents per point; min 50 pts; account balance 200
        LoyaltyPolicy policy = makePolicy(50);
        policy.setPointValueCents(5);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 200L)));

        LoyaltyRedemptionQuoteResponse result = service.getRedemptionQuote(USER_ID, COMPANY_ID, 100);

        assertTrue(result.isValid());
        assertEquals(500L, result.getDiscountCents()); // 100 × 5 = 500
        assertEquals(200L, result.getCurrentBalance());
        assertEquals(100L, result.getBalanceAfterRedemption()); // 200 - 100
    }

    @Test
    void getRedemptionQuote_noAccount_balanceTreatedAsZero() {
        LoyaltyPolicy policy = makePolicy(50);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(Optional.empty());

        LoyaltyRedemptionQuoteResponse result = service.getRedemptionQuote(USER_ID, COMPANY_ID, 100);

        assertFalse(result.isValid()); // balance 0 < 100
        assertEquals(0L, result.getCurrentBalance());
    }

    // ─── applyRedemption ──────────────────────────────────────────────────────

    @Test
    void applyRedemption_zeroPoints_returnsZeroImmediately() {
        long result = service.applyRedemption(USER_ID, COMPANY_ID, ORDER_ID, 0);

        assertEquals(0L, result);
        verifyNoInteractions(policyRepository, accountRepository, transactionRepository);
    }

    @Test
    void applyRedemption_noPolicy_throwsBadRequest() {
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> service.applyRedemption(USER_ID, COMPANY_ID, ORDER_ID, 100));
    }

    @Test
    void applyRedemption_belowMinimum_throwsBadRequest() {
        LoyaltyPolicy policy = makePolicy(200);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));

        assertThrows(BadRequestException.class,
                () -> service.applyRedemption(USER_ID, COMPANY_ID, ORDER_ID, 50)); // < 200
    }

    @Test
    void applyRedemption_noAccount_throwsBadRequest() {
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(Optional.of(makePolicy(50)));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> service.applyRedemption(USER_ID, COMPANY_ID, ORDER_ID, 100));
    }

    @Test
    void applyRedemption_insufficientBalance_throwsConflict() {
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(Optional.of(makePolicy(50)));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 200L)));
        when(accountRepository.deductPoints(eq(ACCOUNT_ID), anyLong())).thenReturn(0); // insufficient

        assertThrows(ConflictException.class,
                () -> service.applyRedemption(USER_ID, COMPANY_ID, ORDER_ID, 100));
    }

    @Test
    void applyRedemption_happyPath_returnsDiscountCentsAndSavesTransaction() {
        LoyaltyPolicy policy = makePolicy(50);
        policy.setPointValueCents(5);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 200L)));
        when(accountRepository.deductPoints(eq(ACCOUNT_ID), anyLong())).thenReturn(1); // success

        long result = service.applyRedemption(USER_ID, COMPANY_ID, ORDER_ID, 100);

        assertEquals(500L, result); // 100 × 5 = 500 cents
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(LoyaltyTransactionType.REDEEM_ORDER, captor.getValue().getType());
        assertEquals(-100, captor.getValue().getPointsDelta());
    }

    // ─── recordOrderEarn ──────────────────────────────────────────────────────

    @Test
    void recordOrderEarn_noPolicy_returnsEarly() {
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.empty());

        service.recordOrderEarn(makeOrder(), COMPANY_ID);

        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void recordOrderEarn_pointsMode_earnsPointsAndSavesTransaction() {
        LoyaltyPolicy policy = makePolicy(0);
        policy.setEarnRatePerDollar(new BigDecimal("1.00")); // 1 pt per $1
        policy.setEarnMode(LoyaltyEarnMode.POINTS);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));

        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 0L);
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(account));

        service.recordOrderEarn(makeOrder(new BigDecimal("50.00")), COMPANY_ID);

        verify(accountRepository).addPoints(ACCOUNT_ID, 50L); // 50 × 1.0 × 1.0 = 50
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(LoyaltyTransactionType.EARN_ORDER, captor.getValue().getType());
        assertEquals(50L, captor.getValue().getPointsDelta());
    }

    @Test
    void recordOrderEarn_zeroEarned_noTransactionSaved() {
        LoyaltyPolicy policy = makePolicy(0);
        policy.setEarnRatePerDollar(new BigDecimal("0.01")); // very low rate
        policy.setEarnMode(LoyaltyEarnMode.POINTS);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 0L)));

        service.recordOrderEarn(makeOrder(new BigDecimal("0.50")), COMPANY_ID); // 0.5 × 0.01 = 0.005 → floor = 0

        verify(accountRepository, never()).addPoints(any(), anyLong());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void recordOrderEarn_withExpiryDays_setsExpiresAt() {
        LoyaltyPolicy policy = makePolicy(0);
        policy.setEarnRatePerDollar(BigDecimal.ONE);
        policy.setEarnMode(LoyaltyEarnMode.POINTS);
        policy.setPointsExpiryDays(365);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 0L)));

        service.recordOrderEarn(makeOrder(new BigDecimal("10.00")), COMPANY_ID);

        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertNotNull(captor.getValue().getExpiresAt());
    }

    @Test
    void recordOrderEarn_nullExpiryDays_noExpiresAt() {
        LoyaltyPolicy policy = makePolicy(0);
        policy.setEarnRatePerDollar(BigDecimal.ONE);
        policy.setEarnMode(LoyaltyEarnMode.POINTS);
        policy.setPointsExpiryDays(null);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 0L)));

        service.recordOrderEarn(makeOrder(new BigDecimal("10.00")), COMPANY_ID);

        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertNull(captor.getValue().getExpiresAt());
    }

    @Test
    void recordOrderEarn_cashbackMode_issuesCreditNotPoints() {
        LoyaltyPolicy policy = makePolicy(0);
        policy.setEarnMode(LoyaltyEarnMode.CASHBACK);
        policy.setCashbackRatePercent(new BigDecimal("10.00")); // 10%
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 0L)));

        service.recordOrderEarn(makeOrder(new BigDecimal("100.00")), COMPANY_ID);

        verify(accountRepository, never()).addPoints(any(), anyLong());
        verify(customerCreditService).issueCredit(eq(USER_ID), any(), any(), any(), any());
    }

    @Test
    void recordOrderEarn_cashbackServiceFails_doesNotThrow() {
        LoyaltyPolicy policy = makePolicy(0);
        policy.setEarnMode(LoyaltyEarnMode.CASHBACK);
        policy.setCashbackRatePercent(new BigDecimal("5.00"));
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 0L)));
        doThrow(new RuntimeException("credit service down"))
                .when(customerCreditService).issueCredit(any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> service.recordOrderEarn(makeOrder(new BigDecimal("100.00")), COMPANY_ID));
    }

    @Test
    void recordOrderEarn_bothMode_earnsPointsAndCashback() {
        LoyaltyPolicy policy = makePolicy(0);
        policy.setEarnRatePerDollar(BigDecimal.ONE);
        policy.setEarnMode(LoyaltyEarnMode.BOTH);
        policy.setCashbackRatePercent(new BigDecimal("5.00"));
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.of(policy));
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 0L)));

        service.recordOrderEarn(makeOrder(new BigDecimal("100.00")), COMPANY_ID);

        verify(accountRepository).addPoints(ACCOUNT_ID, 100L);
        verify(customerCreditService).issueCredit(eq(USER_ID), any(), any(), any(), any());
    }

    // ─── clawbackEarnedPoints ─────────────────────────────────────────────────

    @Test
    void clawbackEarnedPoints_zeroRefund_returnsEarly() {
        service.clawbackEarnedPoints(ORDER_ID, 0L, 10000L);

        verifyNoInteractions(transactionRepository, accountRepository);
    }

    @Test
    void clawbackEarnedPoints_noEarnTransaction_returnsEarly() {
        when(transactionRepository.findFirstBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.EARN_ORDER))
                .thenReturn(Optional.empty());

        service.clawbackEarnedPoints(ORDER_ID, 5000L, 10000L);

        verify(accountRepository, never()).deductPoints(any(), anyLong());
    }

    @Test
    void clawbackEarnedPoints_fullRefund_reversesAllEarnedPoints() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyTransaction earnTx = makeEarnTx(account, 100L, 100L);
        when(transactionRepository.findFirstBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.EARN_ORDER))
                .thenReturn(Optional.of(earnTx));
        when(transactionRepository.sumAbsPointsForOrderAndType(ORDER_ID, LoyaltyTransactionType.EARN_REVERSAL))
                .thenReturn(0L);

        service.clawbackEarnedPoints(ORDER_ID, 10000L, 10000L); // full refund

        verify(accountRepository).deductPoints(ACCOUNT_ID, 100L); // all 100 reversed
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(-100L, captor.getValue().getPointsDelta());
        assertEquals(LoyaltyTransactionType.EARN_REVERSAL, captor.getValue().getType());
    }

    @Test
    void clawbackEarnedPoints_partialRefund_proportionalReversal() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyTransaction earnTx = makeEarnTx(account, 100L, 100L);
        when(transactionRepository.findFirstBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.EARN_ORDER))
                .thenReturn(Optional.of(earnTx));
        when(transactionRepository.sumAbsPointsForOrderAndType(ORDER_ID, LoyaltyTransactionType.EARN_REVERSAL))
                .thenReturn(0L);

        service.clawbackEarnedPoints(ORDER_ID, 5000L, 10000L); // 50% refund

        verify(accountRepository).deductPoints(ACCOUNT_ID, 50L); // floor(100 × 5000/10000) = 50
    }

    @Test
    void clawbackEarnedPoints_alreadyPartiallyReversed_onlyReversesDelta() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 50L);
        LoyaltyTransaction earnTx = makeEarnTx(account, 100L, 100L);
        when(transactionRepository.findFirstBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.EARN_ORDER))
                .thenReturn(Optional.of(earnTx));
        when(transactionRepository.sumAbsPointsForOrderAndType(ORDER_ID, LoyaltyTransactionType.EARN_REVERSAL))
                .thenReturn(50L); // 50 already reversed

        service.clawbackEarnedPoints(ORDER_ID, 10000L, 10000L); // full refund, but 50 already reversed

        verify(accountRepository).deductPoints(ACCOUNT_ID, 50L); // only the remaining 50
    }

    @Test
    void clawbackEarnedPoints_refundExceedsOrderTotal_cappedAtOrderTotal() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyTransaction earnTx = makeEarnTx(account, 100L, 100L);
        when(transactionRepository.findFirstBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.EARN_ORDER))
                .thenReturn(Optional.of(earnTx));
        when(transactionRepository.sumAbsPointsForOrderAndType(ORDER_ID, LoyaltyTransactionType.EARN_REVERSAL))
                .thenReturn(0L);

        // refunded > orderTotal → effectiveRefund is capped at orderTotal
        service.clawbackEarnedPoints(ORDER_ID, 15000L, 10000L);

        // effectiveRefund = min(15000, 10000) = 10000; shouldHaveReversed = floor(100 × 10000/10000) = 100
        verify(accountRepository).deductPoints(ACCOUNT_ID, 100L);
    }

    // ─── restoreRedeemedPoints ────────────────────────────────────────────────

    @Test
    void restoreRedeemedPoints_alreadyRestored_idempotentNoAction() {
        when(transactionRepository.existsBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.RESTORE_ORDER))
                .thenReturn(true);

        service.restoreRedeemedPoints(ORDER_ID);

        verify(accountRepository, never()).addToBalance(any(), anyLong());
    }

    @Test
    void restoreRedeemedPoints_noRedemptionTransaction_doesNothing() {
        when(transactionRepository.existsBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.RESTORE_ORDER))
                .thenReturn(false);
        when(transactionRepository.findFirstBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.REDEEM_ORDER))
                .thenReturn(Optional.empty());

        service.restoreRedeemedPoints(ORDER_ID);

        verify(accountRepository, never()).addToBalance(any(), anyLong());
    }

    @Test
    void restoreRedeemedPoints_happyPath_restoresAndSavesTransaction() {
        when(transactionRepository.existsBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.RESTORE_ORDER))
                .thenReturn(false);

        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyTransaction redeemTx = new LoyaltyTransaction();
        redeemTx.setAccount(account);
        redeemTx.setUserId(USER_ID);
        redeemTx.setCompanyId(COMPANY_ID);
        redeemTx.setType(LoyaltyTransactionType.REDEEM_ORDER);
        redeemTx.setPointsDelta(-200L); // redeemed 200 points
        redeemTx.setValueCents(-1000L);
        redeemTx.setSourceOrderId(ORDER_ID);

        when(transactionRepository.findFirstBySourceOrderIdAndType(ORDER_ID, LoyaltyTransactionType.REDEEM_ORDER))
                .thenReturn(Optional.of(redeemTx));

        service.restoreRedeemedPoints(ORDER_ID);

        verify(accountRepository).addToBalance(ACCOUNT_ID, 200L);
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(LoyaltyTransactionType.RESTORE_ORDER, captor.getValue().getType());
        assertEquals(200L, captor.getValue().getPointsDelta());
    }

    // ─── issueBonus ───────────────────────────────────────────────────────────

    @Test
    void issueBonus_happyPath_addsPointsAndSavesTransaction() {
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 0L)));
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(Optional.of(makePolicy(0)));

        IssueBonusRequest req = new IssueBonusRequest();
        req.setUserId(USER_ID);
        req.setPoints(50);
        req.setReason("VIP reward");

        LoyaltyTransactionResponse result = service.issueBonus(COMPANY_ID, OWNER_ID, req);

        verify(accountRepository).addPoints(ACCOUNT_ID, 50);
        assertNotNull(result);
        assertEquals(LoyaltyTransactionType.EARN_BONUS.name(), result.getType());
    }

    @Test
    void issueBonus_nullReason_usesDefaultReason() {
        when(accountRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 0L)));
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(Optional.of(makePolicy(0)));

        IssueBonusRequest req = new IssueBonusRequest();
        req.setUserId(USER_ID);
        req.setPoints(10);
        req.setReason(null);

        service.issueBonus(COMPANY_ID, OWNER_ID, req);

        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals("Operator bonus", captor.getValue().getReason());
    }

    // ─── adjustPoints ─────────────────────────────────────────────────────────

    @Test
    void adjustPoints_positiveDelta_addsToBalance() {
        when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 100L)));
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(Optional.of(makePolicy(0)));

        AdjustPointsRequest req = new AdjustPointsRequest();
        req.setPointsDelta(50);
        req.setReason("Manual credit");

        service.adjustPoints(ACCOUNT_ID, COMPANY_ID, OWNER_ID, req);

        verify(accountRepository).addToBalance(ACCOUNT_ID, 50);
        verify(accountRepository, never()).deductPoints(any(), anyLong());
    }

    @Test
    void adjustPoints_negativeDelta_deductsPoints() {
        when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 100L)));
        when(accountRepository.deductPoints(eq(ACCOUNT_ID), anyLong())).thenReturn(1);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(Optional.of(makePolicy(0)));

        AdjustPointsRequest req = new AdjustPointsRequest();
        req.setPointsDelta(-30);
        req.setReason("Correction");

        service.adjustPoints(ACCOUNT_ID, COMPANY_ID, OWNER_ID, req);

        verify(accountRepository).deductPoints(ACCOUNT_ID, 30L);
    }

    @Test
    void adjustPoints_negativeInsufficientBalance_throwsBadRequest() {
        when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID))
                .thenReturn(Optional.of(makeAccount(ACCOUNT_ID, 10L)));
        when(accountRepository.deductPoints(any(), anyLong())).thenReturn(0); // insufficient

        AdjustPointsRequest req = new AdjustPointsRequest();
        req.setPointsDelta(-50);

        assertThrows(BadRequestException.class,
                () -> service.adjustPoints(ACCOUNT_ID, COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void adjustPoints_accountNotFound_throwsResourceNotFound() {
        when(accountRepository.findByIdAndCompanyId(ACCOUNT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.adjustPoints(ACCOUNT_ID, COMPANY_ID, OWNER_ID, new AdjustPointsRequest()));
    }

    // ─── createOrUpdatePolicy ─────────────────────────────────────────────────

    @Test
    void createOrUpdatePolicy_deactivatesExistingPolicy() {
        LoyaltyPolicy existing = makePolicy(0);
        existing.setActive(true);
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(Optional.of(existing));
        when(policyRepository.save(any(LoyaltyPolicy.class))).thenAnswer(inv -> {
            LoyaltyPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId(POLICY_ID);
            return p;
        });

        service.createOrUpdatePolicy(COMPANY_ID, OWNER_ID, makeCreatePolicyRequest("POINTS"));

        assertFalse(existing.isActive()); // deactivated
        verify(policyRepository, times(2)).save(any(LoyaltyPolicy.class)); // once to deactivate, once to create
    }

    @Test
    void createOrUpdatePolicy_noExistingPolicy_createsNew() {
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.empty());
        when(policyRepository.save(any(LoyaltyPolicy.class))).thenAnswer(inv -> {
            LoyaltyPolicy p = inv.getArgument(0);
            if (p.getId() == null) p.setId(POLICY_ID);
            return p;
        });

        LoyaltyPolicyResponse result = service.createOrUpdatePolicy(COMPANY_ID, OWNER_ID, makeCreatePolicyRequest("POINTS"));

        assertNotNull(result);
        assertTrue(result.isActive());
    }

    @Test
    void createOrUpdatePolicy_invalidEarnMode_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.createOrUpdatePolicy(COMPANY_ID, OWNER_ID, makeCreatePolicyRequest("INVALID_MODE")));
    }

    // ─── getPolicy ────────────────────────────────────────────────────────────

    @Test
    void getPolicy_found_returnsResponse() {
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(Optional.of(makePolicy(100)));

        LoyaltyPolicyResponse result = service.getPolicy(COMPANY_ID);

        assertNotNull(result);
    }

    @Test
    void getPolicy_notFound_throwsResourceNotFound() {
        when(policyRepository.findFirstByCompanyIdAndActiveTrue(COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getPolicy(COMPANY_ID));
    }

    // ─── createTier / updateTier / listTiers ──────────────────────────────────

    @Test
    void createTier_happyPath_savesTier() {
        when(tierRepository.save(any(LoyaltyTier.class))).thenAnswer(inv -> {
            LoyaltyTier t = inv.getArgument(0);
            if (t.getId() == null) t.setId(TIER_ID);
            return t;
        });

        LoyaltyTierResponse result = service.createTier(COMPANY_ID, OWNER_ID, makeTierRequest());

        verify(tierRepository).save(any(LoyaltyTier.class));
        assertNotNull(result);
        assertEquals("Gold", result.getName());
    }

    @Test
    void updateTier_happyPath_updatesFields() {
        LoyaltyTier tier = makeTier(TIER_ID, COMPANY_ID, "Silver");
        when(tierRepository.findById(TIER_ID)).thenReturn(Optional.of(tier));
        when(tierRepository.save(any(LoyaltyTier.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateLoyaltyTierRequest req = makeTierRequest();
        req.setName("Platinum");

        LoyaltyTierResponse result = service.updateTier(TIER_ID, COMPANY_ID, OWNER_ID, req);

        assertEquals("Platinum", result.getName());
    }

    @Test
    void updateTier_wrongCompany_throwsForbidden() {
        LoyaltyTier tier = makeTier(TIER_ID, TestIds.uuid(99), "Silver"); // different company
        when(tierRepository.findById(TIER_ID)).thenReturn(Optional.of(tier));

        assertThrows(ForbiddenException.class,
                () -> service.updateTier(TIER_ID, COMPANY_ID, OWNER_ID, makeTierRequest()));
    }

    @Test
    void updateTier_notFound_throwsResourceNotFound() {
        when(tierRepository.findById(TIER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateTier(TIER_ID, COMPANY_ID, OWNER_ID, makeTierRequest()));
    }

    @Test
    void listTiers_returnsOrderedList() {
        when(tierRepository.findByCompanyIdOrderByMinPointsAsc(COMPANY_ID))
                .thenReturn(List.of(makeTier(TestIds.uuid(10), COMPANY_ID, "Bronze"),
                        makeTier(TestIds.uuid(11), COMPANY_ID, "Gold")));

        List<LoyaltyTierResponse> result = service.listTiers(COMPANY_ID);

        assertEquals(2, result.size());
        assertEquals("Bronze", result.get(0).getName());
    }

    // ─── expireAccountPoints ──────────────────────────────────────────────────

    @Test
    void expireAccountPoints_noExpiredEarns_doesNothing() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        when(transactionRepository.findExpiredEarns(eq(ACCOUNT_ID), any())).thenReturn(List.of());

        service.expireAccountPoints(account);

        verify(accountRepository, never()).deductPoints(any(), anyLong());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void expireAccountPoints_allClaimsRacedAway_noExpireTx() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyTransaction earn = makeEarnTx(account, 50L, 50L);
        when(transactionRepository.findExpiredEarns(eq(ACCOUNT_ID), any())).thenReturn(List.of(earn));
        when(transactionRepository.claimForExpiry(earn.getId())).thenReturn(0); // already claimed

        service.expireAccountPoints(account);

        verify(accountRepository, never()).deductPoints(any(), anyLong());
    }

    @Test
    void expireAccountPoints_happyPath_deductsAndSavesExpireTx() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyTransaction earn = makeEarnTx(account, 50L, 50L);
        when(transactionRepository.findExpiredEarns(eq(ACCOUNT_ID), any())).thenReturn(List.of(earn));
        when(transactionRepository.claimForExpiry(earn.getId())).thenReturn(1); // claimed
        when(accountRepository.deductPoints(eq(ACCOUNT_ID), anyLong())).thenReturn(1);

        service.expireAccountPoints(account);

        verify(accountRepository).deductPoints(ACCOUNT_ID, 50L);
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(LoyaltyTransactionType.EXPIRE, captor.getValue().getType());
        assertEquals(-50L, captor.getValue().getPointsDelta());
    }

    @Test
    void expireAccountPoints_balanceLessThanExpired_capsAtBalance() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 30L); // only 30 left
        LoyaltyTransaction earn = makeEarnTx(account, 50L, 50L); // 50 expired
        when(transactionRepository.findExpiredEarns(eq(ACCOUNT_ID), any())).thenReturn(List.of(earn));
        when(transactionRepository.claimForExpiry(earn.getId())).thenReturn(1);
        when(accountRepository.deductPoints(eq(ACCOUNT_ID), anyLong())).thenReturn(1);

        service.expireAccountPoints(account);

        verify(accountRepository).deductPoints(ACCOUNT_ID, 30L); // capped at balance
    }

    // ─── issueBirthdayReward ──────────────────────────────────────────────────

    @Test
    void issueBirthdayReward_alreadyClaimed_noAction() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyPolicy policy = makePolicy(0);
        policy.setBirthdayBonusPoints(100);
        when(accountRepository.claimBirthdayRewardForYear(eq(ACCOUNT_ID), anyInt())).thenReturn(0); // already claimed

        service.issueBirthdayReward(account, policy);

        verify(accountRepository, never()).addPoints(any(), anyLong());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void issueBirthdayReward_pointsOnly_addsPointsAndSavesTx() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyPolicy policy = makePolicy(0);
        policy.setBirthdayBonusPoints(100);
        policy.setBirthdayBonusCreditCents(0);
        when(accountRepository.claimBirthdayRewardForYear(eq(ACCOUNT_ID), anyInt())).thenReturn(1);

        service.issueBirthdayReward(account, policy);

        verify(accountRepository).addPoints(ACCOUNT_ID, 100);
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(LoyaltyTransactionType.EARN_BIRTHDAY, captor.getValue().getType());
    }

    @Test
    void issueBirthdayReward_creditServiceFails_doesNotThrow() {
        LoyaltyAccount account = makeAccount(ACCOUNT_ID, 100L);
        LoyaltyPolicy policy = makePolicy(0);
        policy.setBirthdayBonusPoints(0);
        policy.setBirthdayBonusCreditCents(500);
        when(accountRepository.claimBirthdayRewardForYear(eq(ACCOUNT_ID), anyInt())).thenReturn(1);
        doThrow(new RuntimeException("credit down")).when(customerCreditService)
                .issueCredit(any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> service.issueBirthdayReward(account, policy));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private LoyaltyAccount makeAccount(UUID id, long balance) {
        LoyaltyAccount a = new LoyaltyAccount();
        a.setId(id);
        a.setUserId(USER_ID);
        a.setCompanyId(COMPANY_ID);
        a.setPointsBalance(balance);
        a.setLifetimePoints(balance);
        return a;
    }

    private LoyaltyPolicy makePolicy(int minRedemption) {
        LoyaltyPolicy p = new LoyaltyPolicy();
        p.setId(POLICY_ID);
        p.setCompanyId(COMPANY_ID);
        p.setName("Test Policy");
        p.setEarnRatePerDollar(BigDecimal.ONE);
        p.setPointValueCents(1);
        p.setMinRedemptionPoints(minRedemption);
        p.setCashbackRatePercent(BigDecimal.ZERO);
        p.setEarnMode(LoyaltyEarnMode.POINTS);
        p.setActive(true);
        return p;
    }

    private LoyaltyTier makeTier(UUID id, UUID companyId, String name) {
        LoyaltyTier t = new LoyaltyTier();
        t.setId(id);
        t.setCompanyId(companyId);
        t.setName(name);
        t.setMinPoints(0L);
        t.setEarnMultiplier(BigDecimal.ONE);
        return t;
    }

    private LoyaltyTransaction makeEarnTx(LoyaltyAccount account, long points, long valueCents) {
        LoyaltyTransaction tx = new LoyaltyTransaction();
        tx.setId(TX_ID);
        tx.setAccount(account);
        tx.setUserId(account.getUserId());
        tx.setCompanyId(account.getCompanyId());
        tx.setType(LoyaltyTransactionType.EARN_ORDER);
        tx.setPointsDelta(points);
        tx.setValueCents(valueCents);
        tx.setSourceOrderId(ORDER_ID);
        return tx;
    }

    private Order makeOrder() {
        return makeOrder(new BigDecimal("100.00"));
    }

    private Order makeOrder(BigDecimal total) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(ORDER_ID);
        User user = new User();
        user.setId(USER_ID);
        when(order.getUser()).thenReturn(user);
        when(order.getTotalAmount()).thenReturn(total);
        return order;
    }

    private CreateLoyaltyPolicyRequest makeCreatePolicyRequest(String earnMode) {
        CreateLoyaltyPolicyRequest req = new CreateLoyaltyPolicyRequest();
        req.setName("Test Policy");
        req.setEarnRatePerDollar(BigDecimal.ONE);
        req.setPointValueCents(1);
        req.setMinRedemptionPoints(100);
        req.setCashbackRatePercent(BigDecimal.ZERO);
        req.setEarnMode(earnMode);
        return req;
    }

    private CreateLoyaltyTierRequest makeTierRequest() {
        CreateLoyaltyTierRequest req = new CreateLoyaltyTierRequest();
        req.setName("Gold");
        req.setMinPoints(1000L);
        req.setEarnMultiplier(new BigDecimal("2.00"));
        return req;
    }
}
