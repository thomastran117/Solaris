package backend.services.impl.payments;

import backend.models.core.CommissionRecord;
import backend.models.core.MarketplaceVendor;
import backend.models.core.VendorAdjustment;
import backend.models.core.VendorBalance;
import backend.models.core.VendorPayout;
import backend.models.enums.PayoutSchedule;
import backend.models.enums.PayoutStatus;
import backend.models.enums.VendorStatus;
import backend.repositories.CommissionRecordRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorAdjustmentRepository;
import backend.repositories.VendorBalanceRepository;
import backend.repositories.VendorPayoutRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class VendorPayoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(VendorPayoutScheduler.class);

    @Value("${app.payout.scheduler.interval-ms:86400000}")
    private long schedulerIntervalMs;

    private final CommissionRecordRepository commissionRecordRepository;
    private final MarketplaceVendorRepository marketplaceVendorRepository;
    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final VendorBalanceRepository vendorBalanceRepository;
    private final VendorAdjustmentRepository vendorAdjustmentRepository;
    private final VendorPayoutRepository vendorPayoutRepository;
    private final VendorPayoutServiceImpl vendorPayoutService;

    public VendorPayoutScheduler(
            CommissionRecordRepository commissionRecordRepository,
            MarketplaceVendorRepository marketplaceVendorRepository,
            MarketplaceProfileRepository marketplaceProfileRepository,
            VendorBalanceRepository vendorBalanceRepository,
            VendorAdjustmentRepository vendorAdjustmentRepository,
            VendorPayoutRepository vendorPayoutRepository,
            VendorPayoutServiceImpl vendorPayoutService) {
        this.commissionRecordRepository = commissionRecordRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.vendorBalanceRepository = vendorBalanceRepository;
        this.vendorAdjustmentRepository = vendorAdjustmentRepository;
        this.vendorPayoutRepository = vendorPayoutRepository;
        this.vendorPayoutService = vendorPayoutService;
    }

    /**
     * Nightly run (configurable via app.payout.scheduler.interval-ms, default 24h).
     * Three sequential passes:
     * 1. Release pending commission records past their hold period → available balance.
     * 2. Create scheduled payout batches for vendors whose payout cycle is due.
     * 3. Dispatch Stripe transfers for all SCHEDULED payouts.
     */
    @Scheduled(fixedDelayString = "${app.payout.scheduler.interval-ms:86400000}")
    public void runPayoutCycle() {
        log.info("[PAYOUT SCHEDULER] Starting payout cycle");
        releaseHeldBalances();
        createPayoutBatches();
        dispatchScheduledPayouts();
        log.info("[PAYOUT SCHEDULER] Payout cycle complete");
    }

    // -------------------------------------------------------------------------
    // Pass 1: Release hold-period balances
    // -------------------------------------------------------------------------

    private void releaseHeldBalances() {
        // Collect all active marketplace hold periods keyed by marketplaceId
        Map<Long, Integer> holdPeriodByMarketplace = marketplaceProfileRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getCompany().getId(),
                        p -> p.getHoldPeriodDays(),
                        (a, b) -> a));

        // Use the minimum hold period as a conservative cutoff for the initial query,
        // then re-check per record using its marketplace's actual hold period.
        int minHold = holdPeriodByMarketplace.values().stream()
                .mapToInt(Integer::intValue).min().orElse(7);
        Instant conservativeCutoff = Instant.now().minus(minHold, ChronoUnit.DAYS);

        List<CommissionRecord> eligible = commissionRecordRepository.findEligibleForRelease(conservativeCutoff);
        log.info("[PAYOUT SCHEDULER] Found {} commission records candidate for hold release", eligible.size());

        for (CommissionRecord record : eligible) {
            try {
                int holdDays = holdPeriodByMarketplace.getOrDefault(record.getMarketplaceId(), 7);
                Instant actualCutoff = Instant.now().minus(holdDays, ChronoUnit.DAYS);

                if (record.getComputedAt().isAfter(actualCutoff)) {
                    continue; // Still within hold period for this marketplace
                }

                long netCents = record.getNetVendorAmount()
                        .multiply(BigDecimal.valueOf(100)).longValue();

                int updated = vendorBalanceRepository.releasePending(record.getVendorId(), netCents);
                if (updated > 0) {
                    record.setHoldReleased(true);
                    commissionRecordRepository.save(record);
                    log.debug("[PAYOUT SCHEDULER] Released {} cents for vendor {}", netCents, record.getVendorId());
                } else {
                    log.warn("[PAYOUT SCHEDULER] Could not release pending for commission record {} (vendor {}): insufficient pending balance",
                            record.getId(), record.getVendorId());
                }
            } catch (Exception e) {
                log.error("[PAYOUT SCHEDULER] Error releasing hold for commission record {}: {}", record.getId(), e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 2: Create payout batches
    // -------------------------------------------------------------------------

    private static final int PAYOUT_BATCH_PAGE_SIZE = 500;

    private void createPayoutBatches() {
        log.info("[PAYOUT SCHEDULER] Building payout batches");
        int page = 0;
        Slice<VendorBalance> slice;
        do {
            slice = vendorBalanceRepository.findByAvailableCentsGreaterThan(0L,
                    PageRequest.of(page++, PAYOUT_BATCH_PAGE_SIZE));
            processBatchSlice(slice.getContent());
        } while (slice.hasNext());
    }

    private void processBatchSlice(List<VendorBalance> balances) {

        for (VendorBalance balance : balances) {
            try {
                MarketplaceVendor vendor = marketplaceVendorRepository.findById(balance.getVendorId()).orElse(null);
                if (vendor == null || vendor.getStatus() != VendorStatus.APPROVED) continue;
                if (!vendor.isChargesEnabled() || !vendor.isPayoutsEnabled()) continue;

                long marketplaceId = vendor.getMarketplace().getId();
                var profile = marketplaceProfileRepository.findByCompanyId(marketplaceId).orElse(null);
                if (profile == null) continue;

                if (!isPayoutDue(vendor.getId(), profile.getPayoutSchedule())) continue;

                List<CommissionRecord> readyRecords = commissionRecordRepository
                        .findAllByVendorId(vendor.getId()).stream()
                        .filter(r -> r.isHoldReleased()
                                && r.getSubOrder().getPayoutId() == null)
                        .toList();

                List<VendorAdjustment> adjustments =
                        vendorAdjustmentRepository.findAllByVendorIdAndAppliedToPayoutIdIsNull(vendor.getId());

                if (readyRecords.isEmpty() && adjustments.isEmpty()) continue;

                VendorPayout payout = vendorPayoutService.buildAndSavePayout(
                        vendor.getId(), marketplaceId,
                        readyRecords, adjustments,
                        balance.getCurrency());

                // Link sub-orders to this payout
                for (CommissionRecord r : readyRecords) {
                    r.getSubOrder().setPayoutId(payout.getId());
                }

                log.info("[PAYOUT SCHEDULER] Created payout {} for vendor {} ({} records, {} adjustments)",
                        payout.getId(), vendor.getId(), readyRecords.size(), adjustments.size());
            } catch (Exception e) {
                log.error("[PAYOUT SCHEDULER] Error creating payout batch for vendor {}: {}",
                        balance.getVendorId(), e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 3: Dispatch Stripe transfers for SCHEDULED payouts
    // -------------------------------------------------------------------------

    private void dispatchScheduledPayouts() {
        List<VendorPayout> scheduled = vendorPayoutRepository.findAllByStatus(PayoutStatus.SCHEDULED);
        log.info("[PAYOUT SCHEDULER] Dispatching {} scheduled payouts", scheduled.size());

        for (VendorPayout payout : scheduled) {
            try {
                MarketplaceVendor vendor = marketplaceVendorRepository.findById(payout.getVendorId()).orElse(null);
                if (vendor == null) {
                    log.warn("[PAYOUT SCHEDULER] Vendor {} not found for payout {} — skipping", payout.getVendorId(), payout.getId());
                    continue;
                }
                if (vendor.getStatus() != VendorStatus.APPROVED) {
                    log.warn("[PAYOUT SCHEDULER] Vendor {} is {} — skipping payout {}", payout.getVendorId(), vendor.getStatus(), payout.getId());
                    continue;
                }
                if (!vendor.isPayoutsEnabled()) {
                    log.warn("[PAYOUT SCHEDULER] Vendor {} has payoutsEnabled=false — skipping payout {}", payout.getVendorId(), payout.getId());
                    continue;
                }

                long amountCents = payout.getNetAmount()
                        .multiply(BigDecimal.valueOf(100)).longValue();

                vendorPayoutService.dispatchTransfer(payout, vendor.getStripeConnectAccountId(), amountCents);
                vendorPayoutRepository.save(payout);
            } catch (Exception e) {
                log.error("[PAYOUT SCHEDULER] Error dispatching payout {}: {}", payout.getId(), e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isPayoutDue(long vendorId, PayoutSchedule schedule) {
        List<VendorPayout> recent = vendorPayoutRepository.findByVendorIdAndStatusList(vendorId, PayoutStatus.PAID);
        if (recent.isEmpty()) return true;

        Instant lastPaidAt = recent.stream()
                .map(VendorPayout::getPaidAt)
                .filter(t -> t != null)
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);

        Instant now = Instant.now();
        return switch (schedule) {
            case WEEKLY    -> lastPaidAt.isBefore(now.minus(7,  ChronoUnit.DAYS));
            case BIWEEKLY  -> lastPaidAt.isBefore(now.minus(14, ChronoUnit.DAYS));
            case MONTHLY   -> lastPaidAt.isBefore(now.minus(30, ChronoUnit.DAYS));
        };
    }
}
