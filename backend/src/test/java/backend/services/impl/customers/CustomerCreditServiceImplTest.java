package backend.services.impl.customers;

import backend.dtos.requests.credit.IssueCreditRequest;
import backend.dtos.responses.credit.CreditBalanceResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CustomerCredit;
import backend.models.core.User;
import backend.models.enums.CreditEntryType;
import backend.models.enums.UserRole;
import backend.repositories.CustomerCreditRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerCreditServiceImplTest {

    private CustomerCreditRepository creditRepository;
    private UserRepository userRepository;
    private CustomerCreditServiceImpl service;

    @BeforeEach
    void setUp() {
        creditRepository = mock(CustomerCreditRepository.class);
        userRepository   = mock(UserRepository.class);
        service = new CustomerCreditServiceImpl(creditRepository, userRepository);
    }

    // ─── issueCredit ──────────────────────────────────────────────────────────

    @Test
    void issueCredit_createsPositiveLedgerEntry() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));

        when(creditRepository.save(any())).thenAnswer(inv -> {
            CustomerCredit c = inv.getArgument(0);
            c.setUser(customer);
            return c;
        });

        IssueCreditRequest req = new IssueCreditRequest(500L, CreditEntryType.COMPENSATION_ISSUED, "Sorry for the trouble", null);
        service.issueCredit(TestIds.uuid(1), req, TestIds.uuid(2), null, null);

        ArgumentCaptor<CustomerCredit> captor = ArgumentCaptor.forClass(CustomerCredit.class);
        verify(creditRepository).save(captor.capture());
        assertEquals(500L, captor.getValue().getAmountCents());
        assertEquals(CreditEntryType.COMPENSATION_ISSUED, captor.getValue().getType());
    }

    @Test
    void issueCredit_throwsWhenCustomerNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());
        IssueCreditRequest req = new IssueCreditRequest(500L, CreditEntryType.COMPENSATION_ISSUED, null, null);
        assertThrows(ResourceNotFoundException.class,
                () -> service.issueCredit(TestIds.uuid(99), req, TestIds.uuid(2), null, null));
    }

    @Test
    void issueCredit_throwsWhenIssuerIsNotStaff() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User actor    = makeUser(TestIds.uuid(2), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(actor));

        IssueCreditRequest req = new IssueCreditRequest(500L, CreditEntryType.COMPENSATION_ISSUED, null, null);
        assertThrows(ForbiddenException.class,
                () -> service.issueCredit(TestIds.uuid(1), req, TestIds.uuid(2), null, null));
    }

    // ─── getBalance ───────────────────────────────────────────────────────────

    @Test
    void getBalance_returnsSumAndEntries() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(creditRepository.sumBalanceByUserId(eq(TestIds.uuid(1)), any())).thenReturn(1500L);
        when(creditRepository.findAllByUserIdOrderByCreatedAtDesc(TestIds.uuid(1))).thenReturn(List.of());

        CreditBalanceResponse balance = service.getBalance(TestIds.uuid(1));

        assertEquals(1500L, balance.getBalanceCents());
        assertEquals(TestIds.uuid(1), balance.getUserId());
    }

    // ─── redeemCredit ─────────────────────────────────────────────────────────

    @Test
    void redeemCredit_appendsNegativeEntry() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.getReferenceById(TestIds.uuid(1))).thenReturn(customer);
        when(creditRepository.findAllByUserIdForUpdate(TestIds.uuid(1))).thenReturn(List.of());
        when(creditRepository.sumBalanceByUserId(eq(TestIds.uuid(1)), any())).thenReturn(1000L);
        when(creditRepository.save(any())).thenAnswer(inv -> {
            CustomerCredit c = inv.getArgument(0);
            c.setUser(customer);
            return c;
        });

        service.redeemCredit(TestIds.uuid(1), TestIds.uuid(42), 300L);

        ArgumentCaptor<CustomerCredit> captor = ArgumentCaptor.forClass(CustomerCredit.class);
        verify(creditRepository).save(captor.capture());
        assertEquals(-300L, captor.getValue().getAmountCents());
        assertEquals(CreditEntryType.REDEEMED, captor.getValue().getType());
    }

    @Test
    void redeemCredit_throwsWhenInsufficientBalance() {
        when(creditRepository.findAllByUserIdForUpdate(TestIds.uuid(1))).thenReturn(List.of());
        when(creditRepository.sumBalanceByUserId(eq(TestIds.uuid(1)), any())).thenReturn(100L);

        assertThrows(BadRequestException.class,
                () -> service.redeemCredit(TestIds.uuid(1), TestIds.uuid(1), 500L));
    }

    @Test
    void redeemCredit_throwsWhenAmountZero() {
        assertThrows(BadRequestException.class,
                () -> service.redeemCredit(TestIds.uuid(1), TestIds.uuid(1), 0L));
    }

    // ─── reverseCredit ────────────────────────────────────────────────────────

    @Test
    void reverseCredit_appendsOffsettingEntry() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User staff    = makeUser(TestIds.uuid(2), UserRole.SUPPORT);

        CustomerCredit original = new CustomerCredit();
        original.setUser(customer);
        original.setAmountCents(500L);
        original.setType(CreditEntryType.COMPENSATION_ISSUED);

        original.setId(TestIds.uuid(10));
        when(creditRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(creditRepository.claimForReversal(TestIds.uuid(10))).thenReturn(1);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(creditRepository.save(any())).thenAnswer(inv -> {
            CustomerCredit c = inv.getArgument(0);
            c.setUser(customer);
            return c;
        });

        service.reverseCredit(TestIds.uuid(10), TestIds.uuid(2));

        ArgumentCaptor<CustomerCredit> captor = ArgumentCaptor.forClass(CustomerCredit.class);
        verify(creditRepository, times(1)).save(captor.capture());
        assertEquals(-500L, captor.getValue().getAmountCents());
        assertEquals(CreditEntryType.REVERSED, captor.getValue().getType());
    }

    @Test
    void reverseCredit_throwsWhenAlreadyReversed() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        CustomerCredit original = new CustomerCredit();
        original.setUser(customer);
        original.setType(CreditEntryType.REVERSED);
        when(creditRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeUser(TestIds.uuid(2), UserRole.SUPPORT)));

        assertThrows(BadRequestException.class, () -> service.reverseCredit(TestIds.uuid(10), TestIds.uuid(2)));
    }

    @Test
    void reverseCredit_throwsWhenActorIsNotStaff() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User actor    = makeUser(TestIds.uuid(2), UserRole.USER);

        CustomerCredit original = new CustomerCredit();
        original.setId(TestIds.uuid(10));
        original.setUser(customer);
        original.setAmountCents(500L);

        when(creditRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(creditRepository.claimForReversal(TestIds.uuid(10))).thenReturn(1);
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(actor));

        assertThrows(ForbiddenException.class, () -> service.reverseCredit(TestIds.uuid(10), TestIds.uuid(2)));
    }

    // ─── issueCredit (additional) ─────────────────────────────────────────────

    @Test
    void issueCredit_idempotent_returnsExistingEntry() {
        UUID orderIssueId = TestIds.uuid(50);
        CustomerCredit existing = new CustomerCredit();
        existing.setUser(makeUser(TestIds.uuid(1), UserRole.USER));
        existing.setAmountCents(300L);
        existing.setType(CreditEntryType.COMPENSATION_ISSUED);
        when(creditRepository.findFirstBySourceOrderIssueId(orderIssueId))
                .thenReturn(Optional.of(existing));

        IssueCreditRequest req = new IssueCreditRequest(300L, CreditEntryType.COMPENSATION_ISSUED, null, null);
        service.issueCredit(TestIds.uuid(1), req, TestIds.uuid(2), null, orderIssueId);

        verify(creditRepository, never()).save(any());
    }

    @Test
    void issueCredit_staffUserNotFound_throwsResourceNotFound() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        IssueCreditRequest req = new IssueCreditRequest(100L, CreditEntryType.MANUAL_ADJUSTMENT, null, null);
        assertThrows(ResourceNotFoundException.class,
                () -> service.issueCredit(TestIds.uuid(1), req, TestIds.uuid(99), null, null));
    }

    // ─── getBalance (additional) ──────────────────────────────────────────────

    @Test
    void getBalance_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getBalance(TestIds.uuid(99)));
    }

    @Test
    void getBalance_emptyLedger_returnsZeroBalance() {
        when(userRepository.findById(TestIds.uuid(1)))
                .thenReturn(Optional.of(makeUser(TestIds.uuid(1), UserRole.USER)));
        when(creditRepository.sumBalanceByUserId(eq(TestIds.uuid(1)), any())).thenReturn(0L);
        when(creditRepository.findAllByUserIdOrderByCreatedAtDesc(TestIds.uuid(1))).thenReturn(List.of());

        CreditBalanceResponse balance = service.getBalance(TestIds.uuid(1));

        assertEquals(0L, balance.getBalanceCents());
        assertTrue(balance.getEntries().isEmpty());
    }

    @Test
    void getBalance_includesCurrencyUSD() {
        when(userRepository.findById(TestIds.uuid(1)))
                .thenReturn(Optional.of(makeUser(TestIds.uuid(1), UserRole.USER)));
        when(creditRepository.sumBalanceByUserId(eq(TestIds.uuid(1)), any())).thenReturn(500L);
        when(creditRepository.findAllByUserIdOrderByCreatedAtDesc(TestIds.uuid(1))).thenReturn(List.of());

        CreditBalanceResponse balance = service.getBalance(TestIds.uuid(1));

        assertEquals("USD", balance.getCurrency());
    }

    // ─── redeemCredit (additional) ────────────────────────────────────────────

    @Test
    void redeemCredit_exactBalance_succeeds() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.getReferenceById(TestIds.uuid(1))).thenReturn(customer);
        when(creditRepository.findAllByUserIdForUpdate(TestIds.uuid(1))).thenReturn(List.of());
        when(creditRepository.sumBalanceByUserId(eq(TestIds.uuid(1)), any())).thenReturn(500L);
        when(creditRepository.save(any())).thenAnswer(inv -> {
            CustomerCredit c = inv.getArgument(0);
            c.setUser(customer);
            return c;
        });

        assertDoesNotThrow(() -> service.redeemCredit(TestIds.uuid(1), TestIds.uuid(1), 500L));
    }

    // ─── reverseCredit (additional) ───────────────────────────────────────────

    @Test
    void reverseCredit_entryNotFound_throwsResourceNotFound() {
        when(creditRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.reverseCredit(TestIds.uuid(99), TestIds.uuid(2)));
    }

    @Test
    void reverseCredit_alreadyClaimed_throwsBadRequest() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        CustomerCredit original = new CustomerCredit();
        original.setId(TestIds.uuid(10));
        original.setUser(customer);
        original.setAmountCents(500L);
        when(creditRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(creditRepository.claimForReversal(TestIds.uuid(10))).thenReturn(0); // already claimed

        assertThrows(BadRequestException.class,
                () -> service.reverseCredit(TestIds.uuid(10), TestIds.uuid(2)));
    }

    // ─── expireCredits ────────────────────────────────────────────────────────

    @Test
    void expireCredits_expiresEligibleCredits() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        CustomerCredit credit = new CustomerCredit();
        credit.setId(TestIds.uuid(5));
        credit.setUser(customer);
        credit.setAmountCents(200L);
        credit.setType(CreditEntryType.COMPENSATION_ISSUED);
        when(creditRepository.findExpiredCredits(any())).thenReturn(List.of(credit));
        when(creditRepository.claimForExpiry(TestIds.uuid(5))).thenReturn(1);

        service.expireCredits();

        ArgumentCaptor<CustomerCredit> captor = ArgumentCaptor.forClass(CustomerCredit.class);
        verify(creditRepository).save(captor.capture());
        assertEquals(-200L, captor.getValue().getAmountCents());
        assertEquals(CreditEntryType.EXPIRED, captor.getValue().getType());
    }

    @Test
    void expireCredits_alreadyClaimed_skips() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        CustomerCredit credit = new CustomerCredit();
        credit.setId(TestIds.uuid(5));
        credit.setUser(customer);
        credit.setAmountCents(200L);
        when(creditRepository.findExpiredCredits(any())).thenReturn(List.of(credit));
        when(creditRepository.claimForExpiry(TestIds.uuid(5))).thenReturn(0); // race — already claimed

        service.expireCredits();

        verify(creditRepository, never()).save(any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setRole(role);
        return u;
    }
}
