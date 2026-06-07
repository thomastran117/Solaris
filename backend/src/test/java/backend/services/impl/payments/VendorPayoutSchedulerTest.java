package backend.services.impl.payments;

import backend.models.core.CommissionRecord;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.core.SubOrder;
import backend.models.core.User;
import backend.models.core.VendorAdjustment;
import backend.models.core.VendorBalance;
import backend.models.core.VendorPayout;
import backend.models.enums.PayoutSchedule;
import backend.models.enums.PayoutStatus;
import backend.models.enums.UserRole;
import backend.models.enums.VendorStatus;
import backend.repositories.CommissionRecordRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorAdjustmentRepository;
import backend.repositories.VendorBalanceRepository;
import backend.repositories.VendorPayoutRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VendorPayoutSchedulerTest {

    private static final UUID VENDOR_ID = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(2);
    private static final UUID PAYOUT_ID = TestIds.uuid(3);

    private CommissionRecordRepository commissionRecordRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private MarketplaceProfileRepository marketplaceProfileRepository;
    private VendorBalanceRepository vendorBalanceRepository;
    private VendorAdjustmentRepository vendorAdjustmentRepository;
    private VendorPayoutRepository vendorPayoutRepository;
    private VendorPayoutServiceImpl vendorPayoutService;
    private VendorPayoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        commissionRecordRepository = mock(CommissionRecordRepository.class);
        marketplaceVendorRepository = mock(MarketplaceVendorRepository.class);
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        vendorBalanceRepository = mock(VendorBalanceRepository.class);
        vendorAdjustmentRepository = mock(VendorAdjustmentRepository.class);
        vendorPayoutRepository = mock(VendorPayoutRepository.class);
        vendorPayoutService = mock(VendorPayoutServiceImpl.class);

        scheduler = new VendorPayoutScheduler(
                commissionRecordRepository,
                marketplaceVendorRepository,
                marketplaceProfileRepository,
                vendorBalanceRepository,
                vendorAdjustmentRepository,
                vendorPayoutRepository,
                vendorPayoutService);
    }

    @Test
    void runPayoutCycle_releasesEligibleBalances() {
        CommissionRecord record = commissionRecord(true);
        MarketplaceVendor approvedVendor = vendor(true, true, VendorStatus.APPROVED);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of(marketplaceProfile(7)));
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of(record)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(approvedVendor));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorBalanceRepository.releasePending(VENDOR_ID, 9000L)).thenReturn(1);
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorBalanceRepository).releasePending(VENDOR_ID, 9000L);
        verify(commissionRecordRepository).save(record);
    }

    @Test
    void runPayoutCycle_buildsPayoutBatchForDueApprovedVendor() {
        VendorBalance balance = balance();
        MarketplaceVendor vendor = vendor(true, true, VendorStatus.APPROVED);
        CommissionRecord record = commissionRecord(true);
        VendorAdjustment adjustment = new VendorAdjustment();
        adjustment.setVendorId(VENDOR_ID);
        adjustment.setAmountCents(500L);
        adjustment.setCurrency("USD");
        VendorPayout payout = payout();

        when(marketplaceProfileRepository.findAll()).thenReturn(List.of(marketplaceProfile(7)));
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(balance)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(marketplaceProfile(7)));
        when(vendorPayoutRepository.findByVendorIdAndStatusList(VENDOR_ID, PayoutStatus.PAID)).thenReturn(List.of());
        when(commissionRecordRepository.findAllByVendorId(VENDOR_ID)).thenReturn(List.of(record));
        when(vendorAdjustmentRepository.findAllByVendorIdAndAppliedToPayoutIdIsNull(VENDOR_ID)).thenReturn(List.of(adjustment));
        when(vendorPayoutService.buildAndSavePayout(VENDOR_ID, MARKETPLACE_ID, List.of(record), List.of(adjustment), "USD"))
                .thenReturn(payout);
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService).buildAndSavePayout(VENDOR_ID, MARKETPLACE_ID, List.of(record), List.of(adjustment), "USD");
    }

    @Test
    void runPayoutCycle_dispatchesScheduledPayoutsForEligibleVendors() {
        VendorPayout payout = payout();
        MarketplaceVendor vendor = vendor(true, true, VendorStatus.APPROVED);

        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));

        scheduler.runPayoutCycle();

        verify(vendorPayoutService).dispatchTransfer(payout, "acct_123", 9500L);
        verify(vendorPayoutRepository).save(payout);
    }

    @Test
    void runPayoutCycle_skipsDispatchForVendorWithoutPayoutsEnabled() {
        VendorPayout payout = payout();
        MarketplaceVendor vendor = vendor(true, false, VendorStatus.APPROVED);

        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).dispatchTransfer(any(), any(), any(Long.class));
    }

    @Test
    void releaseHeldBalances_skipsNonApprovedVendor() {
        CommissionRecord record = commissionRecord(false);
        MarketplaceVendor suspendedVendor = vendor(true, true, VendorStatus.SUSPENDED);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of(marketplaceProfile(7)));
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of(record)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(suspendedVendor));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorBalanceRepository, never()).releasePending(any(), any(Long.class));
    }

    @Test
    void releaseHeldBalances_skipsVendorNotFound() {
        CommissionRecord record = commissionRecord(false);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of(marketplaceProfile(7)));
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of(record)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.empty());
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorBalanceRepository, never()).releasePending(any(), any(Long.class));
    }

    @Test
    void releaseHeldBalances_skipsRecordStillInHoldPeriod() {
        CommissionRecord record = commissionRecord(false);
        // Set computedAt to very recent (within hold period)
        record.setComputedAt(Instant.now().minusSeconds(60));
        MarketplaceVendor approvedVendor = vendor(true, true, VendorStatus.APPROVED);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of(marketplaceProfile(7)));
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of(record)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(approvedVendor));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorBalanceRepository, never()).releasePending(any(), any(Long.class));
    }

    @Test
    void releaseHeldBalances_releasePendingReturnsZero_doesNotSaveRecord() {
        CommissionRecord record = commissionRecord(false);
        MarketplaceVendor approvedVendor = vendor(true, true, VendorStatus.APPROVED);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of(marketplaceProfile(7)));
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of(record)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(approvedVendor));
        when(vendorBalanceRepository.releasePending(VENDOR_ID, 9000L)).thenReturn(0);
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(commissionRecordRepository, never()).save(record);
    }

    @Test
    void createPayoutBatches_skipsVendorNotApproved() {
        VendorBalance balance = balance();
        MarketplaceVendor pendingVendor = vendor(true, true, VendorStatus.SUSPENDED);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(balance)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(pendingVendor));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).buildAndSavePayout(any(), any(), any(), any(), any());
    }

    @Test
    void createPayoutBatches_skipsVendorWithChargesDisabled() {
        VendorBalance balance = balance();
        MarketplaceVendor vendor = vendor(false, true, VendorStatus.APPROVED);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(balance)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).buildAndSavePayout(any(), any(), any(), any(), any());
    }

    @Test
    void createPayoutBatches_skipsWhenProfileMissing() {
        VendorBalance balance = balance();
        MarketplaceVendor vendor = vendor(true, true, VendorStatus.APPROVED);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(balance)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.empty());
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).buildAndSavePayout(any(), any(), any(), any(), any());
    }

    @Test
    void createPayoutBatches_skipsWhenPayoutNotDue() {
        VendorBalance balance = balance();
        MarketplaceVendor vendor = vendor(true, true, VendorStatus.APPROVED);
        // Recent paid payout → not due
        VendorPayout recentPaid = payout();
        recentPaid.setStatus(PayoutStatus.PAID);
        recentPaid.setPaidAt(Instant.now().minusSeconds(60)); // paid 1 minute ago

        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(balance)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(marketplaceProfile(7)));
        when(vendorPayoutRepository.findByVendorIdAndStatusList(VENDOR_ID, PayoutStatus.PAID))
                .thenReturn(List.of(recentPaid));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).buildAndSavePayout(any(), any(), any(), any(), any());
    }

    @Test
    void createPayoutBatches_skipsWhenNoReadyRecordsOrAdjustments() {
        VendorBalance balance = balance();
        MarketplaceVendor vendor = vendor(true, true, VendorStatus.APPROVED);
        CommissionRecord notReleased = commissionRecord(false);

        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(balance)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(marketplaceProfile(7)));
        when(vendorPayoutRepository.findByVendorIdAndStatusList(VENDOR_ID, PayoutStatus.PAID)).thenReturn(List.of());
        when(commissionRecordRepository.findAllByVendorId(VENDOR_ID)).thenReturn(List.of(notReleased));
        when(vendorAdjustmentRepository.findAllByVendorIdAndAppliedToPayoutIdIsNull(VENDOR_ID)).thenReturn(List.of());
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).buildAndSavePayout(any(), any(), any(), any(), any());
    }

    @Test
    void dispatchScheduledPayouts_skipsWhenVendorNotFound() {
        VendorPayout payout = payout();
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.empty());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).dispatchTransfer(any(), any(), any(Long.class));
    }

    @Test
    void dispatchScheduledPayouts_skipsWhenVendorSuspended() {
        VendorPayout payout = payout();
        MarketplaceVendor suspended = vendor(true, true, VendorStatus.SUSPENDED);
        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(suspended));

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).dispatchTransfer(any(), any(), any(Long.class));
    }

    @Test
    void isPayoutDue_biweekly_notDueIfWithin14Days() {
        VendorBalance balance = balance();
        MarketplaceVendor vendor = vendor(true, true, VendorStatus.APPROVED);
        VendorPayout recentPaid = payout();
        recentPaid.setStatus(PayoutStatus.PAID);
        recentPaid.setPaidAt(Instant.now().minusSeconds(3 * 24 * 3600L)); // 3 days ago — under 14

        MarketplaceProfile biweeklyProfile = marketplaceProfile(7);
        biweeklyProfile.setPayoutSchedule(PayoutSchedule.BIWEEKLY);

        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(balance)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(biweeklyProfile));
        when(vendorPayoutRepository.findByVendorIdAndStatusList(VENDOR_ID, PayoutStatus.PAID))
                .thenReturn(List.of(recentPaid));
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService, never()).buildAndSavePayout(any(), any(), any(), any(), any());
    }

    @Test
    void isPayoutDue_monthly_dueIfMoreThan30DaysAgo() {
        VendorBalance balance = balance();
        MarketplaceVendor vendor = vendor(true, true, VendorStatus.APPROVED);
        VendorPayout oldPaid = payout();
        oldPaid.setStatus(PayoutStatus.PAID);
        oldPaid.setPaidAt(Instant.now().minusSeconds(35 * 24 * 3600L)); // 35 days ago — over 30
        CommissionRecord record = commissionRecord(true);
        VendorPayout newPayout = payout();

        MarketplaceProfile monthlyProfile = marketplaceProfile(7);
        monthlyProfile.setPayoutSchedule(PayoutSchedule.MONTHLY);

        when(marketplaceProfileRepository.findAll()).thenReturn(List.of());
        when(commissionRecordRepository.findEligibleForReleasePaged(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(vendorBalanceRepository.findByAvailableCentsGreaterThan(eq(0L), any(PageRequest.class)))
                .thenReturn(new SliceImpl<>(List.of(balance)));
        when(marketplaceVendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.of(monthlyProfile));
        when(vendorPayoutRepository.findByVendorIdAndStatusList(VENDOR_ID, PayoutStatus.PAID))
                .thenReturn(List.of(oldPaid));
        when(commissionRecordRepository.findAllByVendorId(VENDOR_ID)).thenReturn(List.of(record));
        when(vendorAdjustmentRepository.findAllByVendorIdAndAppliedToPayoutIdIsNull(VENDOR_ID)).thenReturn(List.of());
        when(vendorPayoutService.buildAndSavePayout(any(), any(), any(), any(), any())).thenReturn(newPayout);
        when(vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

        scheduler.runPayoutCycle();

        verify(vendorPayoutService).buildAndSavePayout(any(), any(), any(), any(), any());
    }

    private VendorBalance balance() {
        VendorBalance balance = new VendorBalance();
        balance.setVendorId(VENDOR_ID);
        balance.setAvailableCents(9500L);
        balance.setCurrency("USD");
        return balance;
    }

    private VendorPayout payout() {
        VendorPayout payout = new VendorPayout();
        payout.setId(PAYOUT_ID);
        payout.setVendorId(VENDOR_ID);
        payout.setMarketplaceId(MARKETPLACE_ID);
        payout.setNetAmount(new BigDecimal("95.00"));
        payout.setCurrency("USD");
        payout.setStatus(PayoutStatus.SCHEDULED);
        return payout;
    }

    private CommissionRecord commissionRecord(boolean holdReleased) {
        CommissionRecord record = new CommissionRecord();
        record.setId(TestIds.uuid(10));
        record.setVendorId(VENDOR_ID);
        record.setMarketplaceId(MARKETPLACE_ID);
        record.setGrossAmount(new BigDecimal("100.00"));
        record.setCommissionAmount(new BigDecimal("10.00"));
        record.setNetVendorAmount(new BigDecimal("90.00"));
        record.setCurrency("USD");
        record.setComputedAt(Instant.now().minusSeconds(10 * 24 * 60 * 60L));
        record.setHoldReleased(holdReleased);
        SubOrder subOrder = new SubOrder();
        subOrder.setPayoutId(null);
        record.setSubOrder(subOrder);
        return record;
    }

    private MarketplaceProfile marketplaceProfile(int holdDays) {
        User owner = new User();
        owner.setId(TestIds.uuid(20));
        owner.setRole(UserRole.MERCHANT);
        Company company = new Company();
        company.setId(MARKETPLACE_ID);
        company.setOwner(owner);

        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(company);
        profile.setPayoutSchedule(PayoutSchedule.WEEKLY);
        profile.setHoldPeriodDays(holdDays);
        return profile;
    }

    private MarketplaceVendor vendor(boolean chargesEnabled, boolean payoutsEnabled, VendorStatus status) {
        User owner = new User();
        owner.setId(TestIds.uuid(30));
        owner.setRole(UserRole.MERCHANT);

        Company marketplace = new Company();
        marketplace.setId(MARKETPLACE_ID);
        marketplace.setOwner(owner);

        Company vendorCompany = new Company();
        vendorCompany.setId(TestIds.uuid(31));
        vendorCompany.setOwner(owner);

        MarketplaceVendor vendor = new MarketplaceVendor();
        vendor.setId(VENDOR_ID);
        vendor.setMarketplace(marketplace);
        vendor.setVendorCompany(vendorCompany);
        vendor.setStatus(status);
        vendor.setChargesEnabled(chargesEnabled);
        vendor.setPayoutsEnabled(payoutsEnabled);
        vendor.setStripeConnectAccountId("acct_123");
        return vendor;
    }
}
