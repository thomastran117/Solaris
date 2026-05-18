package backend.services.impl.payments;

import java.util.UUID;
import backend.dtos.requests.marketplace.VendorAdjustmentRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.vendor.VendorAdjustmentResponse;
import backend.dtos.responses.vendor.VendorBalanceResponse;
import backend.dtos.responses.vendor.VendorPayoutItemResponse;
import backend.dtos.responses.vendor.VendorPayoutResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.VendorAdjustment;
import backend.models.core.VendorBalance;
import backend.models.core.VendorPayout;
import backend.models.core.VendorPayoutItem;
import backend.models.enums.PayoutEntryType;
import backend.models.enums.PayoutStatus;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.VendorAdjustmentRepository;
import backend.repositories.VendorBalanceRepository;
import backend.repositories.VendorPayoutItemRepository;
import backend.repositories.VendorPayoutRepository;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.VendorPayoutService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class VendorPayoutServiceImpl implements VendorPayoutService {

    private static final Logger log = LoggerFactory.getLogger(VendorPayoutServiceImpl.class);

    private final VendorPayoutRepository payoutRepository;
    private final VendorPayoutItemRepository payoutItemRepository;
    private final VendorBalanceRepository balanceRepository;
    private final VendorAdjustmentRepository adjustmentRepository;
    private final MarketplaceVendorRepository marketplaceVendorRepository;
    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final PaymentService paymentService;

    public VendorPayoutServiceImpl(
            VendorPayoutRepository payoutRepository,
            VendorPayoutItemRepository payoutItemRepository,
            VendorBalanceRepository balanceRepository,
            VendorAdjustmentRepository adjustmentRepository,
            MarketplaceVendorRepository marketplaceVendorRepository,
            MarketplaceProfileRepository marketplaceProfileRepository,
            PaymentService paymentService) {
        this.payoutRepository = payoutRepository;
        this.payoutItemRepository = payoutItemRepository;
        this.balanceRepository = balanceRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.paymentService = paymentService;
    }

    @Override
    @Transactional(readOnly = true)
    public VendorBalanceResponse getBalance(UUID vendorId, UUID actorUserId) {
        assertVendorOwner(vendorId, actorUserId);
        VendorBalance balance = balanceRepository.findByVendorId(vendorId)
                .orElseGet(() -> emptyBalance(vendorId));
        return toBalanceResponse(balance);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<VendorPayoutResponse> listPayouts(UUID vendorId, PayoutStatus status, int page, int size, UUID actorUserId) {
        assertVendorOwner(vendorId, actorUserId);
        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var results = status != null
                ? payoutRepository.findByVendorIdAndStatus(vendorId, status, pageable)
                : payoutRepository.findByVendorId(vendorId, pageable);
        return new PagedResponse<>(results.map(p -> toPayoutResponse(p, false)));
    }

    @Override
    @Transactional(readOnly = true)
    public VendorPayoutResponse getPayoutDetail(UUID payoutId, UUID vendorId, UUID actorUserId) {
        assertVendorOwner(vendorId, actorUserId);
        VendorPayout payout = payoutRepository.findByIdAndVendorId(payoutId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
        return toPayoutResponse(payout, true);
    }

    @Override
    @Transactional
    public VendorPayoutResponse triggerManualPayout(UUID vendorId, UUID marketplaceId, UUID operatorUserId) {
        assertOperator(marketplaceId, operatorUserId);

        VendorBalance balance = balanceRepository.findByVendorIdForUpdate(vendorId)
                .orElseThrow(() -> new BadRequestException("Vendor has no balance"));

        if (balance.getAvailableCents() <= 0) {
            throw new BadRequestException("No available balance to pay out");
        }

        var vendor = marketplaceVendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        if (vendor.getMarketplace().getId() != marketplaceId) {
            throw new ForbiddenException("Vendor does not belong to this marketplace");
        }

        if (!vendor.isChargesEnabled() || !vendor.isPayoutsEnabled()) {
            throw new BadRequestException("Vendor's Stripe Connect account is not fully enabled for payouts");
        }

        long amountCents = balance.getAvailableCents();
        BigDecimal netAmount = BigDecimal.valueOf(amountCents).movePointLeft(2);

        VendorPayout payout = new VendorPayout();
        payout.setVendorId(vendorId);
        payout.setMarketplaceId(marketplaceId);
        payout.setGrossAmount(netAmount);
        payout.setCommissionAmount(BigDecimal.ZERO);
        payout.setRefundAmount(BigDecimal.ZERO);
        payout.setAdjustmentAmount(BigDecimal.ZERO);
        payout.setNetAmount(netAmount);
        payout.setCurrency(balance.getCurrency());
        payout.setStatus(PayoutStatus.SCHEDULED);
        payout.setScheduledAt(Instant.now());
        VendorPayout saved = payoutRepository.save(payout);

        dispatchTransfer(saved, vendor.getStripeConnectAccountId(), amountCents);
        return toPayoutResponse(payoutRepository.save(saved), false);
    }

    @Override
    @Transactional
    public VendorAdjustmentResponse createAdjustment(UUID vendorId, UUID operatorUserId, VendorAdjustmentRequest request) {
        // operator check — find any marketplace this vendor belongs to and verify operator
        var vendorRecord = marketplaceVendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
        assertOperator(vendorRecord.getMarketplace().getId(), operatorUserId);

        VendorAdjustment adj = new VendorAdjustment();
        adj.setVendorId(vendorId);
        adj.setAmountCents(request.getAmountCents());
        adj.setCurrency(request.getCurrency());
        adj.setReason(request.getReason());
        adj.setCreatedByUserId(operatorUserId);
        VendorAdjustment saved = adjustmentRepository.save(adj);

        // Credit or debit the balance immediately
        if (request.getAmountCents() > 0) {
            balanceRepository.upsertPending(vendorId, 0L, 0L, 0L, request.getCurrency());
            balanceRepository.upsertPending(vendorId, request.getAmountCents(), 0L, 0L, request.getCurrency());
        } else if (request.getAmountCents() < 0) {
            long debit = Math.abs(request.getAmountCents());
            int updated = balanceRepository.releasePending(vendorId, debit);
            if (updated == 0) {
                balanceRepository.releasePending(vendorId, 0); // no-op to avoid null; debit applied below if available
                balanceRepository.moveToInTransit(vendorId, 0);
            }
        }

        return toAdjustmentResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Webhook handlers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void handleTransferPaid(String stripeTransferId) {
        payoutRepository.findByStripeTransferId(stripeTransferId).ifPresent(payout -> {
            if (payout.getStatus() != PayoutStatus.PROCESSING) {
                log.warn("[PAYOUT] Ignoring transfer.paid for payout {} already in status {}", payout.getId(), payout.getStatus());
                return;
            }
            payout.setStatus(PayoutStatus.PAID);
            payout.setPaidAt(Instant.now());
            payoutRepository.save(payout);

            long amountCents = payout.getNetAmount()
                    .multiply(BigDecimal.valueOf(100)).longValue();
            balanceRepository.confirmPayout(payout.getVendorId(), amountCents);
            log.info("[PAYOUT] Transfer {} confirmed PAID for vendor {}", stripeTransferId, payout.getVendorId());
        });
    }

    @Override
    @Transactional
    public void handleTransferFailed(String stripeTransferId, String failureReason) {
        payoutRepository.findByStripeTransferId(stripeTransferId).ifPresent(payout -> {
            if (payout.getStatus() != PayoutStatus.PROCESSING) {
                log.warn("[PAYOUT] Ignoring transfer.failed for payout {} already in status {}", payout.getId(), payout.getStatus());
                return;
            }
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(failureReason);
            payoutRepository.save(payout);

            long amountCents = payout.getNetAmount()
                    .multiply(BigDecimal.valueOf(100)).longValue();
            balanceRepository.returnFromInTransit(payout.getVendorId(), amountCents);
            log.warn("[PAYOUT] Transfer {} FAILED for vendor {}: {}", stripeTransferId, payout.getVendorId(), failureReason);
        });
    }

    // -------------------------------------------------------------------------
    // Package-visible: called by VendorPayoutScheduler
    // -------------------------------------------------------------------------

    @Transactional
    public VendorPayout buildAndSavePayout(UUID vendorId, UUID marketplaceId,
                                            List<backend.models.core.CommissionRecord> records,
                                            List<VendorAdjustment> adjustments,
                                            String currency) {
        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal commissionTotal = BigDecimal.ZERO;
        BigDecimal adjustmentTotal = BigDecimal.ZERO;

        for (var r : records) {
            grossTotal = grossTotal.add(r.getGrossAmount());
            commissionTotal = commissionTotal.add(r.getCommissionAmount());
        }
        for (var a : adjustments) {
            adjustmentTotal = adjustmentTotal.add(
                    BigDecimal.valueOf(a.getAmountCents()).movePointLeft(2));
        }

        BigDecimal netAmount = grossTotal.subtract(commissionTotal).add(adjustmentTotal);

        VendorPayout payout = new VendorPayout();
        payout.setVendorId(vendorId);
        payout.setMarketplaceId(marketplaceId);
        payout.setPeriodStart(records.isEmpty() ? null : records.get(0).getComputedAt());
        payout.setPeriodEnd(records.isEmpty() ? null : records.get(records.size() - 1).getComputedAt());
        payout.setGrossAmount(grossTotal);
        payout.setCommissionAmount(commissionTotal);
        payout.setRefundAmount(BigDecimal.ZERO);
        payout.setAdjustmentAmount(adjustmentTotal);
        payout.setNetAmount(netAmount);
        payout.setCurrency(currency);
        payout.setStatus(PayoutStatus.SCHEDULED);
        payout.setScheduledAt(Instant.now());
        VendorPayout saved = payoutRepository.save(payout);

        for (var r : records) {
            VendorPayoutItem item = new VendorPayoutItem();
            item.setPayout(saved);
            // subOrderId and commissionRecordId are loose FKs still typed Long in entity — not stored until entity migrates
            // item.setSubOrderId(r.getSubOrder().getId());
            // item.setCommissionRecordId(r.getId());
            item.setEntryType(PayoutEntryType.SALE);
            item.setGrossAmount(r.getGrossAmount());
            item.setCommissionAmount(r.getCommissionAmount());
            item.setNetAmount(r.getNetVendorAmount());
            payoutItemRepository.save(item);
        }

        for (var a : adjustments) {
            BigDecimal adjAmt = BigDecimal.valueOf(Math.abs(a.getAmountCents())).movePointLeft(2);
            VendorPayoutItem item = new VendorPayoutItem();
            item.setPayout(saved);
            // adjustmentId is a loose FK still typed Long in entity — not stored until entity migrates
            // item.setAdjustmentId(a.getId());
            item.setEntryType(PayoutEntryType.ADJUSTMENT);
            item.setGrossAmount(adjAmt);
            item.setCommissionAmount(BigDecimal.ZERO);
            item.setNetAmount(a.getAmountCents() >= 0 ? adjAmt : adjAmt.negate());
            payoutItemRepository.save(item);
            // appliedToPayoutId is a loose FK still typed Long in VendorAdjustment entity — not stored until entity migrates
            // a.setAppliedToPayoutId(saved.getId());
            adjustmentRepository.save(a);
        }

        return saved;
    }

    @Transactional
    public void dispatchTransfer(VendorPayout payout, String connectAccountId, long amountCents) {
        if (connectAccountId == null || connectAccountId.isBlank()) {
            log.warn("[PAYOUT] No Stripe Connect account for vendor {} — skipping transfer", payout.getVendorId());
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason("No Stripe Connect account configured");
            return;
        }
        // R2-H4: move the balance first. If the move fails (race, insufficient pending,
        // already reserved by another batch), we abort before talking to Stripe — better
        // to fail fast than leave an orphaned Stripe transfer with no internal accounting
        // record. If the move succeeds but Stripe rejects the transfer, we must roll the
        // balance back so the funds stay queryable as Pending.
        payout.setStatus(PayoutStatus.PROCESSING);
        int moved = balanceRepository.moveToInTransit(payout.getVendorId(), amountCents);
        if (moved == 0) {
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason("Vendor balance insufficient or already reserved");
            log.error("[PAYOUT] Aborted transfer for vendor {} — balance move returned 0",
                    payout.getVendorId());
            return;
        }
        try {
            PaymentService.TransferResult transfer = paymentService.createTransfer(
                    connectAccountId,
                    amountCents,
                    payout.getCurrency().toLowerCase(),
                    "payout_" + payout.getId(),
                    Map.of("vendorId", String.valueOf(payout.getVendorId()),
                           "payoutId", String.valueOf(payout.getId()))
            );
            payout.setStripeTransferId(transfer.transferId());
            log.info("[PAYOUT] Transfer {} dispatched for vendor {}", transfer.transferId(), payout.getVendorId());
        } catch (Exception e) {
            // Reverse the balance move so the funds remain in Pending and a later retry
            // can pick them up. If this rollback itself fails, surface loudly — that's
            // the only path to a real accounting mismatch.
            try {
                balanceRepository.returnFromInTransit(payout.getVendorId(), amountCents);
            } catch (Exception rollbackEx) {
                log.error("[PAYOUT] ACCOUNTING MISMATCH: vendor {} moved {} to in-transit but Stripe transfer failed AND balance rollback also failed. Manual reconciliation required. transfer={} rollback={}",
                        payout.getVendorId(), amountCents, e.getMessage(), rollbackEx.getMessage());
            }
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason("Transfer creation failed: " + e.getMessage());
            log.error("[PAYOUT] Transfer failed for vendor {}: {}", payout.getVendorId(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void assertVendorOwner(UUID vendorId, UUID userId) {
        marketplaceVendorRepository.findById(vendorId)
                .filter(v -> v.getVendorCompany().getOwner().getId() == userId)
                .orElseThrow(() -> new ForbiddenException("You do not own this vendor account"));
    }

    private void assertOperator(UUID marketplaceId, UUID userId) {
        marketplaceProfileRepository.findByCompanyId(marketplaceId)
                .filter(p -> p.getCompany().getOwner().getId() == userId)
                .orElseThrow(() -> new ForbiddenException("You are not an operator of this marketplace"));
    }

    private VendorBalance emptyBalance(UUID vendorId) {
        VendorBalance b = new VendorBalance();
        b.setVendorId(vendorId);
        b.setCurrency("USD");
        return b;
    }

    private VendorBalanceResponse toBalanceResponse(VendorBalance b) {
        return new VendorBalanceResponse(
                b.getVendorId(), b.getPendingCents(), b.getAvailableCents(),
                b.getInTransitCents(), b.getLifetimeGrossCents(), b.getLifetimeCommissionCents(),
                b.getLifetimePaidOutCents(), b.getCurrency(), b.getUpdatedAt());
    }

    private VendorPayoutResponse toPayoutResponse(VendorPayout p, boolean includeItems) {
        List<VendorPayoutItemResponse> items = includeItems
                ? payoutItemRepository.findAllByPayoutId(p.getId()).stream()
                        .map(i -> new VendorPayoutItemResponse(
                                i.getId(),
                                null, // subOrderId is a loose FK still typed Long in entity
                                null, // commissionRecordId is a loose FK still typed Long in entity
                                null, // adjustmentId is a loose FK still typed Long in entity
                                i.getEntryType().name(),
                                i.getGrossAmount(), i.getCommissionAmount(), i.getNetAmount()))
                        .toList()
                : List.of();
        return new VendorPayoutResponse(
                p.getId(), p.getVendorId(), p.getMarketplaceId(),
                p.getPeriodStart(), p.getPeriodEnd(),
                p.getGrossAmount(), p.getCommissionAmount(), p.getRefundAmount(),
                p.getAdjustmentAmount(), p.getNetAmount(), p.getCurrency(),
                p.getStatus().name(), p.getStripeTransferId(), p.getFailureReason(),
                p.getScheduledAt(), p.getPaidAt(), p.getCreatedAt(), p.getUpdatedAt(), items);
    }

    private VendorAdjustmentResponse toAdjustmentResponse(VendorAdjustment a) {
        return new VendorAdjustmentResponse(
                a.getId(), a.getVendorId(), a.getAmountCents(), a.getCurrency(),
                a.getReason(), a.getCreatedByUserId(),
                null, // appliedToPayoutId is a loose FK still typed Long in entity
                a.getCreatedAt());
    }
}
