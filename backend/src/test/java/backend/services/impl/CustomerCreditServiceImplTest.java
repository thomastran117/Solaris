package backend.services.impl;

import java.util.UUID;
import backend.testutil.TestIds;
import backend.dtos.requests.credit.IssueCreditRequest;
import backend.dtos.responses.credit.CreditBalanceResponse;
import backend.dtos.responses.credit.CreditEntryResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CustomerCredit;
import backend.models.core.User;
import backend.models.enums.CreditEntryType;
import backend.models.enums.UserRole;
import backend.repositories.CustomerCreditRepository;
import backend.repositories.UserRepository;
import backend.services.impl.customers.CustomerCreditServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerCreditServiceImplTest {

    private CustomerCreditRepository creditRepository;
    private UserRepository userRepository;
    private CustomerCreditServiceImpl service;

    @BeforeEach
    void setUp() {
        creditRepository = mock(CustomerCreditRepository.class);
        userRepository = mock(UserRepository.class);
        service = new CustomerCreditServiceImpl(creditRepository, userRepository);
    }

    // ─── issueCredit ──────────────────────────────────────────────────────────

    @Test
    void issueCredit_createsPositiveLedgerEntry() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));

        CustomerCredit saved = new CustomerCredit();
        saved.setUser(customer);
        saved.setAmountCents(500L);
        saved.setType(CreditEntryType.COMPENSATION_ISSUED);
        when(creditRepository.save(any())).thenAnswer(inv -> {
            CustomerCredit c = inv.getArgument(0);
            c.setUser(customer);
            return c;
        });

        IssueCreditRequest req = new IssueCreditRequest(500L, CreditEntryType.COMPENSATION_ISSUED, "Sorry for the trouble", null);
        service.issueCredit(1L, req, 2L, null, null);

        ArgumentCaptor<CustomerCredit> captor = ArgumentCaptor.forClass(CustomerCredit.class);
        verify(creditRepository).save(captor.capture());
        assertEquals(500L, captor.getValue().getAmountCents());
        assertEquals(CreditEntryType.COMPENSATION_ISSUED, captor.getValue().getType());
    }

    @Test
    void issueCredit_throwsWhenCustomerNotFound() {
        when(userRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());
        IssueCreditRequest req = new IssueCreditRequest(500L, CreditEntryType.COMPENSATION_ISSUED, null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.issueCredit(99L, req, 2L, null, null));
    }

    @Test
    void issueCredit_throwsWhenIssuerIsNotStaff() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User actor = makeUser(TestIds.uuid(2), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(actor));

        IssueCreditRequest req = new IssueCreditRequest(500L, CreditEntryType.COMPENSATION_ISSUED, null, null);

        assertThrows(ForbiddenException.class, () -> service.issueCredit(1L, req, 2L, null, null));
    }

    // ─── getBalance ───────────────────────────────────────────────────────────

    @Test
    void getBalance_returnsSumAndEntries() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.findById(TestIds.uuid(1))).thenReturn(Optional.of(customer));
        when(creditRepository.sumBalanceByUserId(eq(1L), any())).thenReturn(1500L);
        when(creditRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        CreditBalanceResponse balance = service.getBalance(1L);

        assertEquals(1500L, balance.getBalanceCents());
        assertEquals(1L, balance.getUserId());
    }

    // ─── redeemCredit ─────────────────────────────────────────────────────────

    @Test
    void redeemCredit_appendsNegativeEntry() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        when(userRepository.getReferenceById(TestIds.uuid(1))).thenReturn(customer);
        when(creditRepository.findAllByUserIdForUpdate(1L)).thenReturn(List.of());
        when(creditRepository.sumBalanceByUserId(eq(1L), any())).thenReturn(1000L);
        when(creditRepository.save(any())).thenAnswer(inv -> {
            CustomerCredit c = inv.getArgument(0);
            c.setUser(customer);
            return c;
        });

        service.redeemCredit(1L, 42L, 300L);

        ArgumentCaptor<CustomerCredit> captor = ArgumentCaptor.forClass(CustomerCredit.class);
        verify(creditRepository).save(captor.capture());
        assertEquals(-300L, captor.getValue().getAmountCents());
        assertEquals(CreditEntryType.REDEEMED, captor.getValue().getType());
        assertEquals(42L, captor.getValue().getRedeemedOnOrderId());
    }

    @Test
    void redeemCredit_throwsWhenInsufficientBalance() {
        when(creditRepository.findAllByUserIdForUpdate(1L)).thenReturn(List.of());
        when(creditRepository.sumBalanceByUserId(eq(1L), any())).thenReturn(100L);

        assertThrows(BadRequestException.class, () -> service.redeemCredit(1L, 1L, 500L));
    }

    @Test
    void redeemCredit_throwsWhenAmountZero() {
        assertThrows(BadRequestException.class, () -> service.redeemCredit(1L, 1L, 0L));
    }

    // ─── reverseCredit ────────────────────────────────────────────────────────

    @Test
    void reverseCredit_appendsOffsettingEntry() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User staff = makeUser(TestIds.uuid(2), UserRole.SUPPORT);

        CustomerCredit original = new CustomerCredit();
        original.setUser(customer);
        original.setAmountCents(500L);
        original.setType(CreditEntryType.COMPENSATION_ISSUED);

        when(creditRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(creditRepository.save(any())).thenAnswer(inv -> {
            CustomerCredit c = inv.getArgument(0);
            c.setUser(customer);
            return c;
        });

        service.reverseCredit(10L, 2L);

        ArgumentCaptor<CustomerCredit> captor = ArgumentCaptor.forClass(CustomerCredit.class);
        verify(creditRepository, times(2)).save(captor.capture());
        CustomerCredit reversal = captor.getAllValues().get(0);
        assertEquals(-500L, reversal.getAmountCents());
        assertEquals(CreditEntryType.REVERSED, reversal.getType());
    }

    @Test
    void reverseCredit_throwsWhenAlreadyReversed() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        CustomerCredit original = new CustomerCredit();
        original.setUser(customer);
        original.setType(CreditEntryType.REVERSED);
        when(creditRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeUser(TestIds.uuid(2), UserRole.SUPPORT)));

        assertThrows(BadRequestException.class, () -> service.reverseCredit(10L, 2L));
    }

    @Test
    void reverseCredit_throwsWhenActorIsNotStaff() {
        User customer = makeUser(TestIds.uuid(1), UserRole.USER);
        User actor = makeUser(TestIds.uuid(2), UserRole.USER);

        CustomerCredit original = new CustomerCredit();
        original.setUser(customer);
        original.setAmountCents(500L);

        when(creditRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(actor));

        assertThrows(ForbiddenException.class, () -> service.reverseCredit(10L, 2L));
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
