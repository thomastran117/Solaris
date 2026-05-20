package backend.services.impl.payments;

import backend.dtos.requests.marketplace.VendorAdjustmentRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.vendor.VendorBalanceResponse;
import backend.dtos.responses.vendor.VendorPayoutResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.CommissionRecord;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.SubOrder;
import backend.models.core.User;
import backend.models.core.VendorAdjustment;
import backend.models.core.VendorBalance;
import backend.models.core.VendorPayout;
import backend.models.core.VendorPayoutItem;
import backend.models.enums.PayoutEntryType;
import backend.models.enums.PayoutSchedule;
import backend.models.enums.PayoutStatus;
import backend.models.enums.UserRole;
import backend.models.enums.VendorStatus;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorAdjustmentRepository;
import backend.repositories.VendorBalanceRepository;
import backend.repositories.VendorPayoutItemRepository;
import backend.repositories.VendorPayoutRepository;
import backend.services.intf.payments.PaymentService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VendorPayoutServiceImplTest {

    private static final UUID OWNER_ID = TestIds.uuid(1);
    private static final UUID VENDOR_ID = TestIds.uuid(2);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(3);
    private static final UUID PAYOUT_ID = TestIds.uuid(4);

    private VendorPayoutRepository payoutRepository;
    private VendorPayoutItemRepository payoutItemRepository;
    private VendorBalanceRepository balanceRepository;
    private VendorAdjustmentRepository adjustmentRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private MarketplaceProfileRepository marketplaceProfileRepository;
    private PaymentService paymentService;
    private VendorPayoutServiceImpl service;

    @BeforeEach
    void setUp() {
        payoutRepository = mock(VendorPayoutRepository.class);
        payoutItemRepository = mock(VendorPayoutItemRepository.class);
        balanceRepository = mock(VendorBalanceRepository.class);
        adjustmentRepository = mock(VendorAdjustmentRepository.class);
        marketplaceVendorRepository = mock(MarketplaceVendorRepository.class);
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        paymentService = mock(PaymentService.class);

        service = new VendorPayoutServiceImpl(
                payoutRepository,
                payoutItemRepository,
                balanceRepository,
                adjustmentRepository,
                marketplaceVendorRepository,
                marketplaceProfileRepository,
                paymentService);
    }

    @Test
    void getBalance_returnsEmptyBalanceWhenMissing() {
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(cloneUuid(OWNER_ID))));
        when(balanceRepository.findByVendorId(VENDOR_ID)).thenReturn(Optional.empty());

        VendorBalanceResponse response = service.getBalance(VENDOR_ID, cloneUuid(OWNER_ID));

        assertEquals(VENDOR_ID, response.getVendorId());
        assertEquals(0L, response.getAvailableCents());
        assertEquals("USD", response.getCurrency());
    }

    @Test
    void listPayouts_capsPageSizeAndUsesStatusFilter() {
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OWNER_ID)));
        when(payoutRepository.findByVendorIdAndStatus(eq(VENDOR_ID), eq(PayoutStatus.PAID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payout(PayoutStatus.PAID))));

        PagedResponse<VendorPayoutResponse> response = service.listPayouts(VENDOR_ID, PayoutStatus.PAID, 0, 80, OWNER_ID);

        assertEquals(1, response.getItems().size());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(payoutRepository).findByVendorIdAndStatus(eq(VENDOR_ID), eq(PayoutStatus.PAID), pageableCaptor.capture());
        assertEquals(50, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getPayoutDetail_includesItems() {
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OWNER_ID)));
        when(payoutRepository.findByIdAndVendorId(PAYOUT_ID, VENDOR_ID)).thenReturn(Optional.of(payout(PayoutStatus.PAID)));
        when(payoutItemRepository.findAllByPayoutId(PAYOUT_ID)).thenReturn(List.of(payoutItem()));

        VendorPayoutResponse response = service.getPayoutDetail(PAYOUT_ID, VENDOR_ID, OWNER_ID);

        assertEquals(1, response.getItems().size());
        assertEquals("SALE", response.getItems().get(0).getEntryType());
    }

    @Test
    void triggerManualPayout_dispatchesTransferForEnabledVendor() {
        VendorBalance balance = balance();
        MarketplaceVendor vendor = vendor(OWNER_ID);
        vendor.setChargesEnabled(true);
        vendor.setPayoutsEnabled(true);
        vendor.setStripeConnectAccountId("acct_123");
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(marketplaceProfile(OWNER_ID)));
        when(balanceRepository.findByVendorIdForUpdate(VENDOR_ID)).thenReturn(Optional.of(balance));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(payoutRepository.save(any(VendorPayout.class))).thenAnswer(inv -> {
            VendorPayout p = inv.getArgument(0);
            if (p.getId() == null) p.setId(PAYOUT_ID);
            return p;
        });
        when(paymentService.createTransfer(eq("acct_123"), eq(2500L), eq("usd"), eq("payout_" + PAYOUT_ID), any()))
                .thenReturn(new PaymentService.TransferResult("tr_123", 2500L, "usd", "pending"));
        when(balanceRepository.moveToInTransit(VENDOR_ID, 2500L)).thenReturn(1);

        VendorPayoutResponse response = service.triggerManualPayout(VENDOR_ID, MARKETPLACE_ID, OWNER_ID);

        assertEquals(PAYOUT_ID, response.getId());
        assertEquals("PROCESSING", response.getStatus());
        assertEquals("tr_123", response.getStripeTransferId());
    }

    @Test
    void triggerManualPayout_throwsWhenNoAvailableBalance() {
        VendorBalance balance = balance();
        balance.setAvailableCents(0L);
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(marketplaceProfile(OWNER_ID)));
        when(balanceRepository.findByVendorIdForUpdate(VENDOR_ID)).thenReturn(Optional.of(balance));

        assertThrows(BadRequestException.class,
                () -> service.triggerManualPayout(VENDOR_ID, MARKETPLACE_ID, OWNER_ID));
    }

    @Test
    void createAdjustment_positiveAmountPersistsAndCreditsPending() {
        MarketplaceVendor vendor = vendor(OWNER_ID);
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(marketplaceProfile(OWNER_ID)));
        when(adjustmentRepository.save(any(VendorAdjustment.class))).thenAnswer(inv -> {
            VendorAdjustment adj = inv.getArgument(0);
            adj.setId(TestIds.uuid(10));
            return adj;
        });

        VendorAdjustmentRequest request = new VendorAdjustmentRequest();
        request.setAmountCents(500L);
        request.setCurrency("USD");
        request.setReason("Goodwill");

        var response = service.createAdjustment(VENDOR_ID, OWNER_ID, request);

        assertEquals(500L, response.getAmountCents());
        verify(balanceRepository, times(2)).upsertPending(eq(VENDOR_ID), any(Long.class), eq(0L), eq(0L), eq("USD"));
    }

    @Test
    void handleTransferPaid_marksPaidAndConfirmsBalance() {
        VendorPayout payout = payout(PayoutStatus.PROCESSING);
        payout.setStripeTransferId("tr_1");
        when(payoutRepository.findByStripeTransferId("tr_1")).thenReturn(Optional.of(payout));

        service.handleTransferPaid("tr_1");

        assertEquals(PayoutStatus.PAID, payout.getStatus());
        assertTrue(payout.getPaidAt() != null);
        verify(balanceRepository).confirmPayout(VENDOR_ID, 9500L);
    }

    @Test
    void handleTransferFailed_marksFailedAndReturnsFunds() {
        VendorPayout payout = payout(PayoutStatus.PROCESSING);
        payout.setStripeTransferId("tr_2");
        when(payoutRepository.findByStripeTransferId("tr_2")).thenReturn(Optional.of(payout));

        service.handleTransferFailed("tr_2", "bank rejected");

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        assertEquals("bank rejected", payout.getFailureReason());
        verify(balanceRepository).returnFromInTransit(VENDOR_ID, 9500L);
    }

    @Test
    void buildAndSavePayout_aggregatesRecordsAndAdjustments() {
        when(payoutRepository.save(any(VendorPayout.class))).thenAnswer(inv -> {
            VendorPayout payout = inv.getArgument(0);
            if (payout.getId() == null) payout.setId(PAYOUT_ID);
            return payout;
        });

        CommissionRecord record = commissionRecord(new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"));
        VendorAdjustment adj = new VendorAdjustment();
        adj.setId(TestIds.uuid(20));
        adj.setVendorId(VENDOR_ID);
        adj.setAmountCents(-500L);
        adj.setCurrency("USD");
        adj.setReason("Penalty");

        VendorPayout payout = service.buildAndSavePayout(VENDOR_ID, MARKETPLACE_ID, List.of(record), List.of(adj), "USD");

        assertEquals(new BigDecimal("100.00"), payout.getGrossAmount());
        assertEquals(new BigDecimal("10.00"), payout.getCommissionAmount());
        assertEquals(new BigDecimal("-5.00"), payout.getAdjustmentAmount());
        assertEquals(new BigDecimal("85.00"), payout.getNetAmount());
        verify(payoutItemRepository, times(2)).save(any(VendorPayoutItem.class));
    }

    @Test
    void dispatchTransfer_marksFailedWhenNoConnectAccount() {
        VendorPayout payout = payout(PayoutStatus.SCHEDULED);

        service.dispatchTransfer(payout, "", 9500L);

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        assertTrue(payout.getFailureReason().contains("No Stripe Connect account"));
        verify(paymentService, never()).createTransfer(any(), any(Long.class), any(), any(), any());
    }

    @Test
    void dispatchTransfer_rollsBackWhenPaymentServiceFails() {
        VendorPayout payout = payout(PayoutStatus.SCHEDULED);
        when(balanceRepository.moveToInTransit(VENDOR_ID, 9500L)).thenReturn(1);
        when(paymentService.createTransfer(eq("acct_123"), eq(9500L), eq("usd"), eq("payout_" + PAYOUT_ID), any()))
                .thenThrow(new RuntimeException("stripe down"));

        service.dispatchTransfer(payout, "acct_123", 9500L);

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        assertTrue(payout.getFailureReason().contains("Transfer creation failed"));
        verify(balanceRepository).returnFromInTransit(VENDOR_ID, 9500L);
    }

    private UUID cloneUuid(UUID uuid) {
        return UUID.fromString(uuid.toString());
    }

    private VendorBalance balance() {
        VendorBalance balance = new VendorBalance();
        balance.setVendorId(VENDOR_ID);
        balance.setAvailableCents(2500L);
        balance.setPendingCents(1000L);
        balance.setInTransitCents(0L);
        balance.setCurrency("USD");
        return balance;
    }

    private VendorPayout payout(PayoutStatus status) {
        VendorPayout payout = new VendorPayout();
        payout.setId(PAYOUT_ID);
        payout.setVendorId(VENDOR_ID);
        payout.setMarketplaceId(MARKETPLACE_ID);
        payout.setGrossAmount(new BigDecimal("100.00"));
        payout.setCommissionAmount(new BigDecimal("10.00"));
        payout.setRefundAmount(BigDecimal.ZERO);
        payout.setAdjustmentAmount(new BigDecimal("5.00"));
        payout.setNetAmount(new BigDecimal("95.00"));
        payout.setCurrency("USD");
        payout.setStatus(status);
        payout.setScheduledAt(Instant.parse("2026-05-16T00:00:00Z"));
        payout.setCreatedAt(Instant.parse("2026-05-16T00:00:00Z"));
        payout.setUpdatedAt(Instant.parse("2026-05-17T00:00:00Z"));
        return payout;
    }

    private VendorPayoutItem payoutItem() {
        VendorPayoutItem item = new VendorPayoutItem();
        item.setId(TestIds.uuid(11));
        item.setEntryType(PayoutEntryType.SALE);
        item.setGrossAmount(new BigDecimal("100.00"));
        item.setCommissionAmount(new BigDecimal("10.00"));
        item.setNetAmount(new BigDecimal("90.00"));
        return item;
    }

    private CommissionRecord commissionRecord(BigDecimal gross, BigDecimal commission, BigDecimal net) {
        CommissionRecord record = new CommissionRecord();
        record.setId(TestIds.uuid(30));
        record.setVendorId(VENDOR_ID);
        record.setMarketplaceId(MARKETPLACE_ID);
        record.setGrossAmount(gross);
        record.setCommissionAmount(commission);
        record.setNetVendorAmount(net);
        record.setCurrency("USD");
        record.setComputedAt(Instant.parse("2026-05-10T00:00:00Z"));
        SubOrder subOrder = new SubOrder();
        subOrder.setId(TestIds.uuid(31));
        record.setSubOrder(subOrder);
        return record;
    }

    private MarketplaceVendor vendor(UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setRole(UserRole.MERCHANT);
        Company operatorCompany = new Company();
        operatorCompany.setId(MARKETPLACE_ID);
        operatorCompany.setOwner(owner);
        operatorCompany.setName("Marketplace");

        User vendorOwner = new User();
        vendorOwner.setId(ownerId);
        vendorOwner.setRole(UserRole.VENDOR_OWNER);
        Company vendorCompany = new Company();
        vendorCompany.setId(TestIds.uuid(40));
        vendorCompany.setOwner(vendorOwner);
        vendorCompany.setName("Vendor Co");

        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setId(VENDOR_ID);
        vendor.setMarketplace(operatorCompany);
        vendor.setVendorCompany(vendorCompany);
        vendor.setStatus(VendorStatus.APPROVED);
        return vendor;
    }

    private MarketplaceProfile marketplaceProfile(UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setRole(UserRole.MERCHANT);
        Company company = new Company();
        company.setId(MARKETPLACE_ID);
        company.setOwner(owner);
        company.setName("Marketplace");

        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(company);
        profile.setPayoutSchedule(PayoutSchedule.WEEKLY);
        profile.setHoldPeriodDays(7);
        return profile;
    }
}
