package backend.services.impl.returns;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.return_.BuyerInitiateReturnRequest;
import backend.dtos.requests.return_.BuyerReturnItemRequest;
import backend.dtos.requests.return_.InspectReturnItemRequest;
import backend.dtos.requests.return_.InspectReturnRequest;
import backend.dtos.requests.return_.MerchantApproveReturnRequest;
import backend.dtos.requests.return_.MerchantInitiateReturnRequest;
import backend.dtos.requests.return_.MerchantRejectReturnRequest;
import backend.dtos.responses.return_.ReturnItemResponse;
import backend.dtos.responses.return_.ReturnResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.BundleItem;
import backend.models.core.Company;
import backend.models.core.CompanyReturnLocation;
import backend.models.core.InventoryAdjustment;
import backend.models.core.Order;
import backend.models.core.OrderCompensation;
import backend.models.core.OrderItem;
import backend.models.core.Return;
import backend.models.core.ReturnItem;
import backend.models.core.RiskAssessment;
import backend.models.core.User;
import backend.annotations.retry.RetryOnConcurrency;
import backend.services.intf.AuthAuditLogger;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompensationStatus;
import backend.models.enums.CompensationType;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.models.enums.RefundStatus;
import backend.models.enums.ReturnReason;
import backend.models.enums.ReturnStatus;
import backend.models.enums.RiskAction;
import backend.models.enums.RiskAssessmentKind;
import backend.models.enums.RiskMode;
import backend.repositories.CompanyReturnLocationRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.ReturnItemRepository;
import backend.repositories.ReturnRepository;
import backend.repositories.RiskAssessmentRepository;
import backend.repositories.UserRepository;
import backend.events.activity.ActivityType;
import backend.events.activity.UserActivityEvent;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.promotions.LoyaltyService;
import backend.services.intf.returns.ReturnService;
import backend.models.enums.CompanyCapability;
import backend.services.intf.pricing.RiskEngine;
import backend.services.risk.RiskAssessmentResult;
import backend.services.risk.RiskContext;
import backend.services.risk.RiskSignal;
import backend.utilities.SecurityUtils;
import backend.configurations.environment.RiskProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ReturnServiceImpl implements ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnServiceImpl.class);

    private static final Set<ReturnReason> EVIDENCE_REQUIRED_REASONS = Set.of(
            ReturnReason.DEFECTIVE,
            ReturnReason.WRONG_ITEM,
            ReturnReason.NOT_AS_DESCRIBED,
            ReturnReason.DAMAGED_IN_SHIPPING
    );

    private final ReturnRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final OrderCompensationRepository compensationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final LocationStockRepository locationStockRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CompanyAccessService companyAccessService;
    private final CompanyReturnLocationRepository returnLocationRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final RiskEngine riskEngine;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskProperties riskProperties;
    private final ActivityEventPublisher activityEventPublisher;
    private final LoyaltyService loyaltyService;
    private final AuthAuditLogger auditLogger;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public ReturnServiceImpl(
            ReturnRepository returnRepository,
            ReturnItemRepository returnItemRepository,
            OrderRepository orderRepository,
            OrderCompensationRepository compensationRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            LocationStockRepository locationStockRepository,
            InventoryAdjustmentRepository adjustmentRepository,
            CompanyAccessService companyAccessService,
            CompanyReturnLocationRepository returnLocationRepository,
            UserRepository userRepository,
            PaymentService paymentService,
            RiskEngine riskEngine,
            RiskAssessmentRepository riskAssessmentRepository,
            RiskProperties riskProperties,
            ActivityEventPublisher activityEventPublisher,
            LoyaltyService loyaltyService,
            AuthAuditLogger auditLogger) {
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.orderRepository = orderRepository;
        this.compensationRepository = compensationRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.locationStockRepository = locationStockRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.companyAccessService = companyAccessService;
        this.returnLocationRepository = returnLocationRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.riskEngine = riskEngine;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.riskProperties = riskProperties;
        this.activityEventPublisher = activityEventPublisher;
        this.loyaltyService = loyaltyService;
        this.auditLogger = auditLogger;
    }

    /**
     * Cumulative refunded amount on the order, in cents. Centralised so the loyalty
     * clawback below can compute a proportional reversal off a single, well-defined
     * source instead of recomputing per call-site.
     */
    private static long orderTotalCents(Order order) {
        return order.getTotalAmount() == null
                ? 0L
                : order.getTotalAmount().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }

    /**
     * Strips HTML tags from a free-form text field so a buyer-supplied {@code <script>}
     * or {@code onerror=...} can't reach the merchant dashboard intact. Returns null
     * for null input. Intentionally conservative — anything between {@code <} and
     * {@code >} goes, no HTML allowlist.
     */
    private static String stripHtml(String value) {
        if (value == null) return null;
        return value.replaceAll("<[^>]*>", "").trim();
    }

    // -------------------------------------------------------------------------
    // Buyer-facing
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ReturnResponse requestReturn(UUID orderId, UUID buyerUserId, BuyerInitiateReturnRequest request) {
        // Lock the order so concurrent return requests are serialized — the double-return guard in
        // buildReturnItems reads the aggregate already-returned quantity, which would otherwise race.
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, buyerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException("Return requests can only be submitted for DELIVERED orders");
        }

        int windowDays = riskProperties.getReturnPolicy().getWindowDays();
        if (order.getDeliveredAt() != null) {
            Instant deadline = order.getDeliveredAt().plus(windowDays, java.time.temporal.ChronoUnit.DAYS);
            if (Instant.now().isAfter(deadline)) {
                throw new ConflictException(
                        "Return window of " + windowDays + " days has expired for this order");
            }
        }

        if (EVIDENCE_REQUIRED_REASONS.contains(request.reason()) &&
                (request.evidenceUrls() == null || request.evidenceUrls().isEmpty())) {
            throw new BadRequestException(
                    "At least one evidence image is required for reason " + request.reason());
        }

        Return ret = new Return();
        ret.setOrder(order);
        ret.setRequestedBy(userRepository.getReferenceById(buyerUserId));
        ret.setStatus(ReturnStatus.REQUESTED);
        ret.setReason(request.reason());
        // R2-L2: strip HTML tags before persisting — the merchant portal may render the
        // note in a panel, and an unescaped {@code <script>} or {@code onload=} would
        // be an XSS vector. Server-side defense-in-depth costs us nothing and means a
        // future frontend bug can't be exploited.
        ret.setBuyerNote(stripHtml(request.buyerNote()));
        ret.setEvidenceUrls(request.evidenceUrls() != null ? new ArrayList<>(request.evidenceUrls()) : new ArrayList<>());

        List<ReturnItem> returnItems = buildReturnItems(ret, request.items(), order, null);
        ret.setItems(returnItems);

        Return saved = returnRepository.save(ret);

        for (ReturnItem ri : saved.getItems()) {
            OrderItem oi = ri.getOrderItem();
            if (oi == null || oi.getProduct() == null) continue;
            UUID mkt = oi.getProduct().getMarketplaceId();
            if (mkt == null) continue;
            activityEventPublisher.publish(new UserActivityEvent(
                    buyerUserId, null, oi.getProduct().getId(), mkt, ActivityType.RETURN, Instant.now()));
        }

        return toReturnResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReturnResponse> getReturnsByOrder(UUID orderId, UUID buyerUserId) {
        orderRepository.findByIdAndUserId(orderId, buyerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return returnRepository.findAllByOrderId(orderId).stream()
                .map(this::toReturnResponse)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Merchant-facing
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ReturnResponse> getCompanyReturnsByOrder(UUID orderId, UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);
        return returnRepository.findAllByOrderIdAndCompanyId(orderId, companyId).stream()
                .filter(ret -> returnBelongsExclusivelyToCompany(ret, companyId))
                .map(this::toReturnResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnResponse getCompanyReturn(UUID returnId, UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);
        Return ret = requireCompanyScopedReturn(
                returnRepository.findByIdAndCompanyId(returnId, companyId),
                returnId,
                companyId);
        return toReturnResponse(ret);
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public ReturnResponse approveReturn(UUID returnId, UUID companyId, UUID ownerId, MerchantApproveReturnRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Return ret = requireCompanyScopedReturn(
                returnRepository.findByIdAndCompanyIdForUpdate(returnId, companyId),
                returnId,
                companyId);

        if (ret.getStatus() != ReturnStatus.REQUESTED) {
            throw new ConflictException("Return " + returnId + " is not in REQUESTED status (current: " + ret.getStatus() + ")");
        }

        CompanyReturnLocation location = resolveReturnLocation(company, request.returnLocationId());
        snapshotReturnAddress(ret, location);

        ret.setApprovedAt(Instant.now());
        ret.setMerchantNote(request.merchantNote());

        // Risk check on the return itself (abuse / serial-returner detection). VERIFY and BLOCK both
        // collapse to "reject the return" on this path — there is no mid-flow buyer step-up here.
        // This MUST run before any order-item mutation: the order items are managed entities, so
        // marking them RETURNED before the check would flush even when the return is rejected.
        RiskAssessmentResult riskResult = assessReturn(ret);
        if (riskProperties.getMode() == RiskMode.ENFORCE
                && (riskResult.action() == RiskAction.BLOCK || riskResult.action() == RiskAction.VERIFY)) {
            String topReason = riskResult.signals().stream()
                    .filter(s -> s.scoreContribution() > 0)
                    .findFirst()
                    .map(RiskSignal::reason)
                    .orElse("Risk engine flagged this return");
            ret.setStatus(ReturnStatus.REJECTED);
            ret.setMerchantNote("Auto-rejected by risk engine: " + topReason);
            return toReturnResponse(returnRepository.save(ret));
        }

        // Approved: mark items RETURNED so order status can advance — stock restoration is deferred
        // to inspectReturn().
        for (ReturnItem ri : ret.getItems()) {
            ri.getOrderItem().setFulfillmentStatus(FulfillmentStatus.RETURNED);
        }

        issueRefundAtApproval(ret, request.refundAmountOverrideCents());
        computeOrderStatusAfterReturn(ret.getOrder());

        auditLogger.log(AuthAuditLogger.Event.REFUND_ISSUED, ownerId.toString(),
                "returnId=" + ret.getId()
                + " orderId=" + ret.getOrder().getId()
                + (request.refundAmountOverrideCents() != null
                        ? " overrideCents=" + request.refundAmountOverrideCents() : ""));

        orderRepository.save(ret.getOrder());
        return toReturnResponse(returnRepository.save(ret));
    }

    private RiskAssessmentResult assessReturn(Return ret) {
        User buyer = ret.getRequestedBy();
        Order order = ret.getOrder();
        RiskContext ctx = new RiskContext(
                buyer != null ? buyer.getId() : order.getUser().getId(),
                buyer != null ? buyer.getEmail() : order.getUser().getEmail(),
                buyer != null ? buyer.getCreatedAt() : order.getUser().getCreatedAt(),
                null,
                java.util.Collections.emptySet(),
                order.getId(),
                order.getTotalAmount(),
                order.getDeliveredAt(),
                order.getCurrency(),
                null,
                null,
                java.util.Collections.emptyList(),
                null,
                null,
                null,
                null,
                null,
                RiskAssessmentKind.RETURN,
                Instant.now());

        RiskAssessmentResult result = riskEngine.assess(ctx);
        try {
            RiskAssessment assessment = new RiskAssessment();
            assessment.setOrderId(order.getId());
            assessment.setUserId(ctx.userId());
            assessment.setDecision(result.action());
            assessment.setScore(result.totalScore());
            assessment.setMode(riskProperties.getMode());
            assessment.setKind(RiskAssessmentKind.RETURN);
            assessment.setReasonsJson(serializeRiskSignals(result));
            riskAssessmentRepository.save(assessment);
        } catch (Exception ex) {
            log.warn("Failed to persist return-path risk assessment for returnId={}", ret.getId(), ex);
        }
        return result;
    }

    private String serializeRiskSignals(RiskAssessmentResult result) {
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
            for (RiskSignal sig : result.signals()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("type", sig.type().name());
                row.put("decision", sig.decision().name());
                row.put("score", sig.scoreContribution());
                row.put("reason", sig.reason());
                rows.add(row);
            }
            payload.put("signals", rows);
            payload.put("warnings", result.warnings());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public ReturnResponse inspectReturn(UUID returnId, UUID companyId, UUID ownerId, InspectReturnRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Return ret = requireCompanyScopedReturn(
                returnRepository.findByIdAndCompanyIdForUpdate(returnId, companyId),
                returnId,
                companyId);

        if (ret.getStatus() != ReturnStatus.APPROVED) {
            throw new ConflictException("Return " + returnId + " must be APPROVED before inspection (current: " + ret.getStatus() + ")");
        }

        if (request.merchantNote() != null) {
            ret.setMerchantNote(request.merchantNote());
        }

        boolean anyRestockFailed = false;
        for (InspectReturnItemRequest ir : request.items()) {
            ReturnItem ri = ret.getItems().stream()
                    .filter(item -> item.getId().equals(ir.returnItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Return item " + ir.returnItemId() + " not found in return " + returnId));

            ri.setCondition(ir.condition());
            // Guard on isStockRestored so a duplicate/retried inspection can't restore the same
            // item twice (the FOR UPDATE lock above serializes concurrent inspections).
            if (ir.restock() && !ri.isStockRestored()) {
                try {
                    restoreReturnedItemStock(ri);
                    ri.setStockRestored(true);
                } catch (Exception e) {
                    log.error("Failed to restore stock for return item {} during inspection: {}", ri.getId(), e.getMessage());
                    anyRestockFailed = true;
                }
            }
        }

        if (anyRestockFailed) {
            returnRepository.save(ret);
            throw new ConflictException("Return " + returnId + " inspection failed: one or more items could not be restocked. Retry after resolving the inventory issue.");
        }

        ret.setStatus(ReturnStatus.COMPLETED);
        ret.setCompletedAt(Instant.now());

        orderRepository.save(ret.getOrder());
        return toReturnResponse(returnRepository.save(ret));
    }

    @Override
    @Transactional
    public ReturnResponse rejectReturn(UUID returnId, UUID companyId, UUID ownerId, MerchantRejectReturnRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Return ret = requireCompanyScopedReturn(
                returnRepository.findByIdAndCompanyId(returnId, companyId),
                returnId,
                companyId);

        if (ret.getStatus() != ReturnStatus.REQUESTED) {
            throw new ConflictException("Return " + returnId + " is not in REQUESTED status (current: " + ret.getStatus() + ")");
        }

        ret.setStatus(ReturnStatus.REJECTED);
        ret.setMerchantNote(request.merchantNote());

        return toReturnResponse(returnRepository.save(ret));
    }

    @Override
    @Transactional
    @RetryOnConcurrency
    public ReturnResponse merchantInitiateReturn(UUID orderId, UUID companyId, UUID ownerId, MerchantInitiateReturnRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.FULFILL_ORDERS);

        Order order = orderRepository.findByIdAndProductCompanyIdForUpdate(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.SHIPPED) {
            throw new ConflictException("Cannot initiate return for order " + orderId
                    + ": must be DELIVERED or SHIPPED (current: " + order.getStatus() + ")");
        }

        Return ret = new Return();
        ret.setOrder(order);
        ret.setRequestedBy(null);   // merchant-initiated
        ret.setStatus(ReturnStatus.REQUESTED);
        ret.setReason(request.reason());
        ret.setMerchantNote(request.merchantNote());
        ret.setRestockItems(request.restockItems());
        ret.setApprovedAt(Instant.now());

        CompanyReturnLocation location = resolveReturnLocation(company, request.returnLocationId());
        snapshotReturnAddress(ret, location);

        List<ReturnItem> returnItems = buildReturnItems(ret, request.items(), order, companyId);
        ret.setItems(returnItems);

        RiskAssessmentResult riskResult = assessReturn(ret);
        if (riskProperties.getMode() == RiskMode.ENFORCE
                && (riskResult.action() == RiskAction.BLOCK || riskResult.action() == RiskAction.VERIFY)) {
            String topReason = riskResult.signals().stream()
                    .filter(s -> s.scoreContribution() > 0)
                    .findFirst()
                    .map(RiskSignal::reason)
                    .orElse("Risk engine flagged this return");
            throw new ConflictException("Return rejected by risk engine: " + topReason);
        }

        processReturnItems(ret, request.restockItems());
        issueRefundAndFinalize(ret, request.refundAmountOverrideCents());

        orderRepository.save(order);
        return toReturnResponse(returnRepository.save(ret));
    }

    // -------------------------------------------------------------------------
    // Support operations
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    @RetryOnConcurrency
    public ReturnResponse issuePartialRefund(UUID orderId, long amountCents, String reason, UUID actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (amountCents <= 0) {
            throw new BadRequestException("Refund amount must be greater than zero");
        }

        String note = reason != null ? reason : "Partial refund issued by support staff #" + actorUserId;

        Return ret = new Return();
        ret.setOrder(order);
        ret.setRequestedBy(null);
        ret.setStatus(ReturnStatus.REQUESTED);
        ret.setMerchantNote(note);
        ret.setRestockItems(false);
        ret.setApprovedAt(Instant.now());
        ret.setItems(new ArrayList<>());

        issueRefundAndFinalize(ret, amountCents);
        orderRepository.save(order);
        ReturnResponse response = toReturnResponse(returnRepository.save(ret));
        auditLogger.log(AuthAuditLogger.Event.REFUND_ISSUED,
                actorUserId.toString(),
                "orderId=" + orderId + " amountCents=" + amountCents + " returnId=" + response.id());
        return response;
    }

    // -------------------------------------------------------------------------
    // Webhook
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    @RetryOnConcurrency
    public void handleRefundWebhookEvent(String stripeRefundId, String stripeStatus, long amountCents) {
        returnRepository.findByStripeRefundId(stripeRefundId).ifPresent(ret -> {
            RefundStatus newStatus = "succeeded".equals(stripeStatus) ? RefundStatus.SUCCEEDED : RefundStatus.FAILED;
            ret.setRefundStatus(newStatus);
            Order order = ret.getOrder();

            if (newStatus == RefundStatus.SUCCEEDED) {
                // R2-C4: atomic delta against the column instead of read-then-write.
                // Two near-simultaneous SUCCEEDED webhooks for the same refund will
                // both compute the same delta (amountCents - prev); applying both
                // atomically would over-credit, so we *also* guard idempotency on the
                // Return row's own refundStatus — if it's already SUCCEEDED with the
                // same amount, skip the order-side update entirely.
                long prev = ret.getRefundedAmountCents() != null ? ret.getRefundedAmountCents() : 0L;
                boolean alreadyApplied = ret.getRefundStatus() == RefundStatus.SUCCEEDED
                        && prev == amountCents;
                if (!alreadyApplied) {
                    long delta = amountCents - prev;
                    orderRepository.addRefundAmountDelta(order.getId(), delta);
                    ret.setRefundedAmountCents(amountCents);
                }
                ret.setRefundFailureReason(null);
                // The atomic UPDATE clears the persistence context, so reload the Order
                // before downstream code uses it for status computation / clawback.
                Order fresh = orderRepository.findById(order.getId()).orElse(order);
                computeOrderStatusAfterReturn(fresh);
                // R2-C1 / R2-M7: claw back the proportional share of any points earned
                // on this order. Computed off the *total* refunded so far, so a second
                // partial refund extends rather than overlaps the previous reversal.
                try {
                    loyaltyService.clawbackEarnedPoints(
                            fresh.getId(),
                            fresh.getRefundedAmountCents(),
                            orderTotalCents(fresh));
                } catch (Exception ex) {
                    log.error("[LOYALTY] Clawback failed for order {} after refund {}: {}",
                            fresh.getId(), stripeRefundId, ex.getMessage());
                }
                returnRepository.save(ret);
                orderRepository.save(fresh);
            } else {
                long provisional = ret.getRefundedAmountCents() != null ? ret.getRefundedAmountCents() : 0L;
                if (provisional > 0) {
                    orderRepository.addRefundAmountDelta(order.getId(), -provisional);
                }
                ret.setRefundedAmountCents(0L);
                ret.setRefundFailureReason("Stripe webhook reported failure for refund " + stripeRefundId);
                recordReturnCompensation(order, ret);
                Order fresh = orderRepository.findById(order.getId()).orElse(order);
                computeOrderStatusAfterReturn(fresh);
                returnRepository.save(ret);
                orderRepository.save(fresh);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates and constructs ReturnItem rows for the given item requests.
     * Enforces the double-return guard: queued + new ≤ orderItem.quantity.
     * Looks up OrderItems from the already-loaded order.getItems() collection.
     */
    private List<ReturnItem> buildReturnItems(
            Return ret,
            List<BuyerReturnItemRequest> itemRequests,
            Order order,
            UUID expectedCompanyId) {
        List<ReturnItem> returnItems = new ArrayList<>();
        UUID scopedCompanyId = expectedCompanyId;
        for (BuyerReturnItemRequest ir : itemRequests) {
            OrderItem orderItem = order.getItems().stream()
                    .filter(i -> i.getId().equals(ir.orderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Order item " + ir.orderItemId() + " not found in order " + order.getId()));

            UUID itemCompanyId = resolveOwningCompanyId(orderItem);
            if (scopedCompanyId == null) {
                scopedCompanyId = itemCompanyId;
            } else if (!scopedCompanyId.equals(itemCompanyId)) {
                if (expectedCompanyId != null) {
                    throw new BadRequestException(
                            "Order item " + ir.orderItemId() + " does not belong to company " + expectedCompanyId);
                }
                throw new BadRequestException(
                        "Return requests must be split by merchant. Submit separate returns for each company.");
            }

            if (orderItem.getFulfillmentStatus() != FulfillmentStatus.DELIVERED
                    && orderItem.getFulfillmentStatus() != FulfillmentStatus.SHIPPED) {
                throw new BadRequestException("Order item " + ir.orderItemId()
                        + " cannot be returned (fulfillment status: " + orderItem.getFulfillmentStatus() + ")");
            }

            int alreadyQueued = returnItemRepository.sumReturnedQuantityByOrderItemId(ir.orderItemId());
            if (alreadyQueued + ir.quantityToReturn() > orderItem.getQuantity()) {
                throw new BadRequestException("Cannot return " + ir.quantityToReturn()
                        + " unit(s) of item " + ir.orderItemId() + ": only "
                        + (orderItem.getQuantity() - alreadyQueued) + " unit(s) remain returnable");
            }

            ReturnItem ri = new ReturnItem();
            ri.setReturnRequest(ret);
            ri.setOrderItem(orderItem);
            ri.setQuantityReturned(ir.quantityToReturn());
            returnItems.add(ri);
        }
        return returnItems;
    }

    private Return requireCompanyScopedReturn(Optional<Return> maybeReturn, UUID returnId, UUID companyId) {
        Return ret = maybeReturn.orElseThrow(() -> new ResourceNotFoundException("Return not found with id: " + returnId));
        if (!returnBelongsExclusivelyToCompany(ret, companyId)) {
            throw new ResourceNotFoundException("Return not found with id: " + returnId);
        }
        return ret;
    }

    private boolean returnBelongsExclusivelyToCompany(Return ret, UUID companyId) {
        return ret.getItems() != null
                && !ret.getItems().isEmpty()
                && ret.getItems().stream()
                .map(ReturnItem::getOrderItem)
                .allMatch(item -> companyId.equals(findOwningCompanyId(item)));
    }

    private UUID resolveOwningCompanyId(OrderItem orderItem) {
        UUID companyId = findOwningCompanyId(orderItem);
        if (companyId == null) {
            throw new BadRequestException(
                    "Order item " + orderItem.getId() + " has no company owner and cannot be returned");
        }
        return companyId;
    }

    private UUID findOwningCompanyId(OrderItem orderItem) {
        if (orderItem.getBundle() != null && orderItem.getBundle().getCompany() != null) {
            return orderItem.getBundle().getCompany().getId();
        }
        if (orderItem.getProduct() != null && orderItem.getProduct().getCompany() != null) {
            return orderItem.getProduct().getCompany().getId();
        }
        return null;
    }

    /**
     * Marks order items as RETURNED and optionally restores inventory for each ReturnItem.
     */
    private void processReturnItems(Return ret, boolean restockItems) {
        boolean anyRestockFailed = false;
        for (ReturnItem ri : ret.getItems()) {
            ri.getOrderItem().setFulfillmentStatus(FulfillmentStatus.RETURNED);

            if (restockItems && !ri.isStockRestored()) {
                try {
                    restoreReturnedItemStock(ri);
                    ri.setStockRestored(true);
                } catch (Exception e) {
                    log.error("Failed to restore stock for return item {} (order item {}): {}",
                            ri.getId(), ri.getOrderItem().getId(), e.getMessage());
                    anyRestockFailed = true;
                }
            }
        }
        // Do NOT finalize/refund a restock-requested return when restoration failed — abort so the
        // enclosing transaction rolls back and the merchant can retry after fixing the inventory issue.
        if (anyRestockFailed) {
            throw new ConflictException(
                    "Return could not be completed: one or more items could not be restocked. "
                    + "Retry after resolving the inventory issue.");
        }
    }

    /**
     * Issues the Stripe refund at approval time and sets the return status to APPROVED
     * (awaiting physical inspection). If the refund call fails, status is set to FAILED.
     * Stock restoration is NOT performed here — it happens later in inspectReturn().
     */
    private void issueRefundAtApproval(Return ret, Long refundAmountOverrideCents) {
        Order order = ret.getOrder();
        long refundAmountCents = resolveRefundAmount(ret, refundAmountOverrideCents);

        if (refundAmountCents > 0 && order.getPaymentIntentId() != null) {
            try {
                PaymentService.RefundResult result = paymentService.refundPayment(order.getPaymentIntentId(), refundAmountCents);
                ret.setStripeRefundId(result.id());
                ret.setRefundedAmountCents(result.amountInCents());
                ret.setRefundStatus(RefundStatus.PENDING);  // Stripe confirms async via webhook
                order.setRefundedAmountCents(order.getRefundedAmountCents() + result.amountInCents());
                ret.setStatus(ReturnStatus.APPROVED);
            } catch (Exception e) {
                log.error("Failed to issue refund for return on order {}: {}", order.getId(), e.getMessage());
                ret.setRefundedAmountCents(refundAmountCents);
                ret.setRefundStatus(RefundStatus.FAILED);
                ret.setRefundFailureReason(e.getMessage());
                recordReturnCompensation(order, ret);
                ret.setStatus(ReturnStatus.FAILED);
                ret.setCompletedAt(Instant.now());
            }
        } else if (refundAmountCents == 0) {
            ret.setRefundedAmountCents(0L);
            ret.setRefundStatus(RefundStatus.NONE);
            ret.setStatus(ReturnStatus.APPROVED);
        } else {
            ret.setStatus(ReturnStatus.APPROVED);
        }
    }

    /**
     * Issues the refund and immediately finalizes the return as COMPLETED (used by
     * merchantInitiateReturn() where the merchant has already inspected the items).
     */
    private void issueRefundAndFinalize(Return ret, Long refundAmountOverrideCents) {
        Order order = ret.getOrder();
        long refundAmountCents = resolveRefundAmount(ret, refundAmountOverrideCents);

        if (refundAmountCents > 0 && order.getPaymentIntentId() != null) {
            try {
                PaymentService.RefundResult result = paymentService.refundPayment(order.getPaymentIntentId(), refundAmountCents);
                ret.setStripeRefundId(result.id());
                ret.setRefundedAmountCents(result.amountInCents());
                ret.setRefundStatus(RefundStatus.PENDING);  // Stripe confirms async via webhook
                order.setRefundedAmountCents(order.getRefundedAmountCents() + result.amountInCents());
            } catch (Exception e) {
                log.error("Failed to issue refund for return on order {}: {}", order.getId(), e.getMessage());
                ret.setRefundedAmountCents(refundAmountCents);
                ret.setRefundStatus(RefundStatus.FAILED);
                ret.setRefundFailureReason(e.getMessage());
                recordReturnCompensation(order, ret);
                ret.setStatus(ReturnStatus.FAILED);
                ret.setCompletedAt(Instant.now());
                computeOrderStatusAfterReturn(order);
                return;
            }
        } else if (refundAmountCents == 0) {
            ret.setRefundedAmountCents(0L);
            ret.setRefundStatus(RefundStatus.NONE);
        }

        ret.setStatus(ReturnStatus.COMPLETED);
        ret.setCompletedAt(Instant.now());
        computeOrderStatusAfterReturn(order);
    }

    /**
     * Resolves the final refund amount in cents.
     * - Override non-null → use directly (0 = waive).
     * - Override null → auto-calculate: SUM(unitPrice × quantityReturned) converted to cents.
     */
    private long resolveRefundAmount(Return ret, Long refundAmountOverrideCents) {
        if (refundAmountOverrideCents != null) {
            if (refundAmountOverrideCents < 0) {
                throw new BadRequestException("Refund override amount cannot be negative");
            }
            Order order = ret.getOrder();
            long orderTotal = orderTotalCents(order);
            long alreadyRefunded = java.util.Objects.requireNonNullElse(order.getRefundedAmountCents(), 0L);
            long maxRefundable = Math.max(0L, orderTotal - alreadyRefunded);
            if (refundAmountOverrideCents > maxRefundable) {
                throw new BadRequestException(
                        "Refund override (" + refundAmountOverrideCents + " cents) exceeds the remaining refundable amount ("
                                + maxRefundable + " cents)");
            }
            return refundAmountOverrideCents;
        }
        Order order = ret.getOrder();

        // Line unit prices already include promotion/coupon savings, but order-level loyalty and
        // premium discounts are applied to the order total only — never pushed back onto line items.
        // Refunding raw line prices would (a) over-refund in aggregate and (b) let the first partial
        // return drain the whole balance, leaving later returns under-refunded. So pro-rate the
        // order-level discount across every line by value: scale = collected / sum(all line values),
        // capped at 1 (tax/shipping must not inflate a refund above line value — same conservative
        // pro-rata used for marketplace sub-orders). Each returned line then refunds its fair share.
        BigDecimal orderLineSum = BigDecimal.ZERO;
        for (OrderItem oi : order.getItems()) {
            if (oi.getUnitPrice() != null) {
                orderLineSum = orderLineSum.add(
                        oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
            }
        }
        BigDecimal collected = order.getTotalAmount() != null ? order.getTotalAmount() : orderLineSum;
        BigDecimal scale = (orderLineSum.signum() > 0 && collected.compareTo(orderLineSum) < 0)
                ? collected.divide(orderLineSum, 10, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        BigDecimal returnedValue = BigDecimal.ZERO;
        for (ReturnItem ri : ret.getItems()) {
            returnedValue = returnedValue.add(ri.getOrderItem().getUnitPrice()
                    .multiply(BigDecimal.valueOf(ri.getQuantityReturned())));
        }
        long computed = returnedValue.multiply(scale)
                .multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValue();

        // Final safety net: never exceed the order's remaining refundable balance (also guards
        // cumulative drift across multiple partial returns) — the same ceiling the override path uses.
        long alreadyRefunded = java.util.Objects.requireNonNullElse(order.getRefundedAmountCents(), 0L);
        long maxRefundable = Math.max(0L, orderTotalCents(order) - alreadyRefunded);
        return Math.min(computed, maxRefundable);
    }

    /**
     * Restores stock for a single ReturnItem, using quantityReturned (not the full orderItem.quantity).
     * Handles product, variant, bundle, and location stock restoration.
     */
    private void restoreReturnedItemStock(ReturnItem ri) {
        OrderItem item = ri.getOrderItem();
        int qty = ri.getQuantityReturned();

        if (item.getBundle() != null) {
            for (BundleItem bi : item.getBundle().getItems()) {
                int total = qty * bi.getQuantity();
                if (bi.getVariant() != null) {
                    variantRepository.restoreStock(bi.getVariant().getId(), total);
                } else {
                    productRepository.restoreStock(bi.getProduct().getId(), total);
                }
            }
            recordReturnAdjustment(item, ri.getReturnRequest().getOrder().getId(), qty);
            return;
        }

        if (item.getVariant() != null) {
            variantRepository.restoreStock(item.getVariant().getId(), qty);
        } else if (item.getProduct() != null) {
            productRepository.restoreStock(item.getProduct().getId(), qty);
        }

        // Restore location-level stock. If this fails, reverse the product/variant restore
        // above so the two levels stay consistent rather than committing a partial state.
        try {
            if (item.getFulfillmentLocation() != null && item.getProduct() != null) {
                UUID variantRef = item.getVariant() != null ? item.getVariant().getId() : null;
                locationStockRepository
                        .findByLocationIdAndProductIdAndVariantRef(
                                item.getFulfillmentLocation().getId(), item.getProduct().getId(), variantRef)
                        .ifPresent(ls -> locationStockRepository.restoreStock(ls.getId(), qty));
            }
        } catch (Exception locationEx) {
            // Location restore failed — attempt to roll back the product/variant restore so
            // both levels stay consistent. If the compensating decrement also fails, log
            // prominently so an operator can reconcile the stock drift manually.
            try {
                if (item.getVariant() != null) {
                    variantRepository.decrementStock(item.getVariant().getId(), qty);
                } else if (item.getProduct() != null) {
                    productRepository.decrementStock(item.getProduct().getId(), qty);
                }
            } catch (Exception rollbackEx) {
                log.error("[STOCK-DRIFT] Location restore AND rollback both failed for order item {} qty={}: "
                        + "location_error={} rollback_error={}",
                        item.getId(), qty, locationEx.getMessage(), rollbackEx.getMessage());
            }
            throw locationEx;
        }

        recordReturnAdjustment(item, ri.getReturnRequest().getOrder().getId(), qty);
    }

    /**
     * Creates an InventoryAdjustment audit record for a returned item.
     */
    private void recordReturnAdjustment(OrderItem item, UUID orderId, int qty) {
        try {
            InventoryAdjustment adj = new InventoryAdjustment();
            adj.setProduct(item.getProduct());
            adj.setVariant(item.getVariant());
            adj.setDelta(qty);
            adj.setPreviousStock(0);    // pre-restore stock reading would be a TOCTOU race
            adj.setNewStock(0);
            adj.setReason(AdjustmentReason.FULFILLMENT_RETURN);
            adj.setNote("Return for order #" + orderId);
            adj.setOrderId(orderId);
            adjustmentRepository.save(adj);
        } catch (Exception e) {
            log.error("Failed to record return adjustment for order {}: {}", orderId, e.getMessage());
        }
    }

    /**
     * Recomputes and sets the order's top-level status after return processing.
     *
     * RETURNED  = all items physically returned (refund may be waived or pending)
     * REFUNDED  = all items returned AND order has been fully refunded
     */
    private void computeOrderStatusAfterReturn(Order order) {
        boolean allReturned = order.getItems().stream()
                .allMatch(i -> i.getFulfillmentStatus() == FulfillmentStatus.RETURNED
                        || i.getFulfillmentStatus() == FulfillmentStatus.CANCELLED);

        long orderTotalCents = order.getTotalAmount()
                .multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValue();

        boolean fullyRefunded = order.getRefundedAmountCents() >= orderTotalCents;

        if (allReturned && fullyRefunded) {
            order.setStatus(OrderStatus.REFUNDED);
        } else if (allReturned) {
            order.setStatus(OrderStatus.RETURNED);
        }
        // partial return → keep current status (DELIVERED / SHIPPED)
    }

    /**
     * Records a PAYMENT_REFUND compensation entry for a failed refund so the
     * OrderCompensationScheduler can retry it. Uses a structured detail format so
     * retryCompensation() can extract the partial amount and avoid over-refunding.
     */
    private void recordReturnCompensation(Order order, Return ret) {
        OrderCompensation comp = new OrderCompensation();
        comp.setOrder(order);
        comp.setType(CompensationType.PAYMENT_REFUND);
        comp.setStatus(CompensationStatus.FAILED);
        comp.setDetail("REFUND_PARTIAL:" + order.getPaymentIntentId()
                + ":CENTS:" + (ret.getRefundedAmountCents() != null ? ret.getRefundedAmountCents() : 0L));
        comp.setErrorMessage(ret.getRefundFailureReason());
        comp.setAttempts(1);
        compensationRepository.save(comp);
    }

    /**
     * Resolves which CompanyReturnLocation to use for a return.
     * If locationId is provided, it must belong to the company.
     * Otherwise the primary location is used, falling back to the first available.
     * Throws ConflictException if the company has no return locations at all.
     */
    private CompanyReturnLocation resolveReturnLocation(Company company, UUID locationId) {
        if (locationId != null) {
            return returnLocationRepository.findByIdAndCompanyId(locationId, company.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Return location " + locationId + " not found for company " + company.getId()));
        }
        // Single query ordered by is_primary DESC, id ASC — returns primary if set, else oldest location.
        return returnLocationRepository.findFirstByCompanyIdOrderByPrimaryDescIdAsc(company.getId())
                .orElseThrow(() -> new ConflictException(
                        "Company has no return locations configured. "
                        + "Add one at POST /companies/" + company.getId() + "/return-locations"));
    }

    /** Snapshots the return location's address onto the Return entity at approval time. */
    private void snapshotReturnAddress(Return ret, CompanyReturnLocation loc) {
        ret.setReturnShipToAddress(loc.getAddress());
        ret.setReturnShipToCity(loc.getCity());
        ret.setReturnShipToCountry(loc.getCountry());
        ret.setReturnShipToPostalCode(loc.getPostalCode());
    }

    // -------------------------------------------------------------------------
    // Response mapping
    // -------------------------------------------------------------------------

    private ReturnResponse toReturnResponse(Return ret) {
        List<ReturnItemResponse> itemResponses = ret.getItems().stream()
                .map(this::toReturnItemResponse)
                .toList();

        return new ReturnResponse(
                ret.getId(),
                ret.getOrder().getId(),
                ret.getRequestedBy() != null ? ret.getRequestedBy().getId() : null,
                ret.getStatus().name(),
                ret.getReason() != null ? ret.getReason().name() : null,
                ret.getBuyerNote(),
                ret.getMerchantNote(),
                ret.isRestockItems(),
                itemResponses,
                ret.getEvidenceUrls(),
                ret.getReturnShipToAddress(),
                ret.getReturnShipToCity(),
                ret.getReturnShipToCountry(),
                ret.getReturnShipToPostalCode(),
                ret.getRefundedAmountCents(),
                ret.getRefundStatus().name(),
                ret.getCreatedAt(),
                ret.getUpdatedAt(),
                ret.getApprovedAt(),
                ret.getCompletedAt()
        );
    }

    private ReturnItemResponse toReturnItemResponse(ReturnItem ri) {
        OrderItem oi = ri.getOrderItem();
        return new ReturnItemResponse(
                ri.getId(),
                oi.getId(),
                oi.getProductName(),
                oi.getVariant() != null ? oi.getVariant().getId() : null,
                oi.getVariantTitle(),
                ri.getQuantityReturned(),
                oi.getUnitPrice(),
                ri.isStockRestored(),
                ri.getCondition() != null ? ri.getCondition().name() : null
        );
    }
}
