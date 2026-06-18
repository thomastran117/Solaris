package backend.services.impl.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import backend.events.webhook.OutboundWebhookEvent;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.RestockRequest;
import backend.models.enums.RestockStatus;
import backend.models.enums.WebhookEventType;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.RestockRequestRepository;
import backend.services.intf.OutboundWebhookEventPublisher;
import backend.services.intf.support.EmailService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates low-stock and out-of-stock conditions after any stock decrement.
 * When a threshold is breached, it:
 *  1. Logs a warning to the terminal.
 *  2. Emails the company owner asynchronously.
 *  3. Auto-creates a PENDING RestockRequest if the product has autoRestockEnabled=true
 *     and no active (PENDING/IN_TRANSIT) request already exists.
 *
 * Supports both fixed-quantity thresholds and percent-of-maxStock thresholds.
 * Inject into any service that decrements stock.
 */
@Component
public class StockAlertService {

    private static final Logger log = LoggerFactory.getLogger(StockAlertService.class);

    private static final List<RestockStatus> ACTIVE_RESTOCK_STATUSES =
            List.of(RestockStatus.PENDING, RestockStatus.IN_TRANSIT);

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final RestockRequestRepository restockRequestRepository;
    private final EmailService emailService;
    private final OutboundWebhookEventPublisher outboundWebhookEventPublisher;

    public StockAlertService(ProductRepository productRepository,
                             ProductVariantRepository variantRepository,
                             RestockRequestRepository restockRequestRepository,
                             EmailService emailService,
                             OutboundWebhookEventPublisher outboundWebhookEventPublisher) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.restockRequestRepository = restockRequestRepository;
        this.emailService = emailService;
        this.outboundWebhookEventPublisher = outboundWebhookEventPublisher;
    }

    /**
     * Checks product or variant stock after a decrement and alerts if any configured
     * threshold (fixed quantity OR percent of maxStock) is breached.
     * Also auto-creates a restock request if the product is configured for it.
     *
     * @param productId   product ID (always present)
     * @param productName product name for readable output
     * @param variantId   null for product-level stock; variant ID for variant-level
     * @param variantSku  null for product-level; variant SKU for variant-level
     * @param newStock    the stock value AFTER the decrement
     * @param threshold   configured fixed low-stock threshold (null = no quantity threshold)
     */
    public void checkAndAlert(
            UUID productId,
            String productName,
            UUID variantId,
            String variantSku,
            int newStock,
            Integer threshold) {

        // Resolve percent threshold + auto-restock settings from the stored entity.
        // We keep a reference to the loaded variant (if any) to use when creating the restock request.
        Integer resolvedThresholdPercent = null;
        Integer resolvedMaxStock = null;
        boolean resolvedAutoRestockEnabled = false;
        Integer resolvedAutoRestockQty = null;
        ProductVariant loadedVariant = null;

        if (variantId != null) {
            loadedVariant = variantRepository.findById(variantId).orElse(null);
            if (loadedVariant != null) {
                resolvedThresholdPercent = loadedVariant.getLowStockThresholdPercent();
                resolvedMaxStock = loadedVariant.getMaxStock();
                resolvedAutoRestockEnabled = loadedVariant.isAutoRestockEnabled();
                resolvedAutoRestockQty = loadedVariant.getAutoRestockQty();
            }
        } else {
            Product p = productRepository.findById(productId).orElse(null);
            if (p != null) {
                resolvedThresholdPercent = p.getLowStockThresholdPercent();
                resolvedMaxStock = p.getMaxStock();
                resolvedAutoRestockEnabled = p.isAutoRestockEnabled();
                resolvedAutoRestockQty = p.getAutoRestockQty();
            }
        }

        final Integer thresholdPercent = resolvedThresholdPercent;
        final Integer maxStock = resolvedMaxStock;
        final Integer autoRestockQty = resolvedAutoRestockQty;

        boolean quantityBreached = threshold != null && newStock <= threshold;
        boolean percentBreached = thresholdPercent != null && maxStock != null
                && maxStock > 0 && (newStock * 100.0 / maxStock) <= thresholdPercent;

        if (!quantityBreached && !percentBreached) return;

        boolean outOfStock = newStock == 0;

        // 1. Terminal log
        if (variantId != null) {
            if (outOfStock) {
                log.warn("[STOCK ALERT] OUT OF STOCK — product: '{}' (id={}) | variant SKU: '{}' (id={})",
                        productName, productId, variantSku, variantId);
            } else {
                log.warn("[STOCK ALERT] LOW STOCK — product: '{}' (id={}) | variant SKU: '{}' (id={}) | stock: {} (threshold: {}, thresholdPct: {}%)",
                        productName, productId, variantSku, variantId, newStock, threshold, thresholdPercent);
            }
        } else {
            if (outOfStock) {
                log.warn("[STOCK ALERT] OUT OF STOCK — product: '{}' (id={})",
                        productName, productId);
            } else {
                log.warn("[STOCK ALERT] LOW STOCK — product: '{}' (id={}) | stock: {} (threshold: {}, thresholdPct: {}%)",
                        productName, productId, newStock, threshold, thresholdPercent);
            }
        }

        // Load product with company + owner for email and restock request creation
        final ProductVariant finalVariant = loadedVariant;
        final boolean doAutoRestock = resolvedAutoRestockEnabled && autoRestockQty != null && autoRestockQty >= 1;

        productRepository.findByIdWithCompanyOwner(productId).ifPresent(product -> {

            // 2. Email the company owner (async, with retry)
            String ownerEmail = product.getCompany().getOwner().getEmail();
            String ownerFirstName = product.getCompany().getOwner().getFirstName();
            emailService.sendLowStockAlertEmail(
                    ownerEmail, ownerFirstName,
                    productId, productName,
                    variantId, variantSku,
                    newStock, threshold,
                    outOfStock);

            // 3a. Outbound webhook — STOCK_LOW
            try {
                UUID companyId = product.getCompany().getId();
                // Map.of() does not allow null values — use LinkedHashMap for optional fields
                java.util.Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
                payloadMap.put("eventType", WebhookEventType.STOCK_LOW.name());
                payloadMap.put("productId", productId.toString());
                payloadMap.put("productName", productName);
                payloadMap.put("variantId", variantId != null ? variantId.toString() : "");
                payloadMap.put("variantSku", variantSku != null ? variantSku : "");
                payloadMap.put("newStock", newStock);
                payloadMap.put("companyId", companyId.toString());
                payloadMap.put("occurredAt", Instant.now().toString());
                String payload = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(payloadMap);
                outboundWebhookEventPublisher.publish(new OutboundWebhookEvent(
                        WebhookEventType.STOCK_LOW, companyId, null, productId, payload, Instant.now()));
            } catch (Exception e) {
                log.warn("[WEBHOOK] Failed to publish STOCK_LOW webhook for product {}: {}", productId, e.getMessage());
            }

            // 3. Auto-restock: create a PENDING RestockRequest if none already active
            if (doAutoRestock) {
                boolean activeExists = variantId != null
                        ? restockRequestRepository.existsByProductIdAndVariantIdAndStatusIn(
                                productId, variantId, ACTIVE_RESTOCK_STATUSES)
                        : restockRequestRepository.existsByProductIdAndVariantIsNullAndStatusIn(
                                productId, ACTIVE_RESTOCK_STATUSES);

                if (!activeExists) {
                    RestockRequest rr = new RestockRequest();
                    rr.setCompany(product.getCompany());
                    rr.setProduct(product);
                    rr.setVariant(finalVariant);
                    rr.setRequestedQty(autoRestockQty != null ? autoRestockQty : 0);
                    rr.setStatus(RestockStatus.PENDING);
                    rr.setCreatedBy(product.getCompany().getOwner());
                    rr.setSupplierNote("Auto-generated — stock reached " + newStock
                            + " unit" + (newStock == 1 ? "" : "s") + " for " + productName
                            + (variantSku != null ? " (SKU: " + variantSku + ")" : ""));
                    restockRequestRepository.save(rr);
                    log.info("[AUTO RESTOCK] Created PENDING restock request for product '{}' (id={}) qty={}",
                            productName, productId, rr.getRequestedQty());
                } else {
                    log.debug("[AUTO RESTOCK] Skipped — active restock request already exists for product '{}' (id={})",
                            productName, productId);
                }
            }
        });
    }

    /**
     * Checks location-level stock after a decrement and logs a warning if at/below threshold.
     * Email notifications are not sent for location-level alerts.
     */
    public void checkAndAlertLocation(
            UUID locationStockId,
            String locationName,
            UUID productId,
            String productName,
            UUID variantId,
            int newStock,
            Integer threshold) {

        if (threshold == null || newStock > threshold) return;

        if (newStock == 0) {
            log.warn("[STOCK ALERT] LOCATION OUT OF STOCK — location: '{}' | product: '{}' (id={}) | variantId: {} | locationStockId: {}",
                    locationName, productName, productId, variantId, locationStockId);
        } else {
            log.warn("[STOCK ALERT] LOCATION LOW STOCK — location: '{}' | product: '{}' (id={}) | variantId: {} | locationStockId: {} | stock: {} (threshold: {})",
                    locationName, productName, productId, variantId, locationStockId, newStock, threshold);
        }
    }
}
