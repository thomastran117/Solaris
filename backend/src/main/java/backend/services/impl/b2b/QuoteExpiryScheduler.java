package backend.services.impl.b2b;

import backend.services.intf.CacheService;
import backend.services.intf.b2b.B2BInvoiceService;
import backend.services.intf.b2b.B2BQuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Periodically expires B2B quotes that have passed their {@code expiresAt} while still awaiting the
 * buyer, and flips past-due net-terms invoices to OVERDUE (Feature 12). Uses a Redis distributed
 * lock so only one instance runs per cycle, mirroring {@code ProductAnalyticsWorker}.
 */
@Component
public class QuoteExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuoteExpiryScheduler.class);
    private static final String LOCK_KEY = "b2b:quote:expiry:lock";
    private static final long LOCK_TTL_S = 55 * 60L; // just under the default hourly schedule

    private final B2BQuoteService quoteService;
    private final B2BInvoiceService invoiceService;
    private final CacheService cacheService;
    private final String instanceId = UUID.randomUUID().toString();

    public QuoteExpiryScheduler(B2BQuoteService quoteService, B2BInvoiceService invoiceService,
                                CacheService cacheService) {
        this.quoteService = quoteService;
        this.invoiceService = invoiceService;
        this.cacheService = cacheService;
    }

    @Scheduled(fixedDelayString = "${app.b2b.quote-expiry-check-interval-ms:3600000}")
    public void runMaintenance() {
        try {
            if (!cacheService.tryLock(LOCK_KEY, instanceId, LOCK_TTL_S)) {
                log.debug("[B2B] Skipping maintenance — another instance holds the lock");
                return;
            }
        } catch (Exception e) {
            log.warn("[B2B] Maintenance lock acquisition failed, proceeding without lock: {}", e.getMessage());
        }
        try {
            // Independent steps: an exception in one must not skip the other.
            try {
                quoteService.expireStaleQuotes();
            } catch (Exception e) {
                log.warn("[B2B] Quote expiry run failed: {}", e.getMessage());
            }
            try {
                invoiceService.markOverdueInvoices();
            } catch (Exception e) {
                log.warn("[B2B] Invoice overdue sweep failed: {}", e.getMessage());
            }
        } finally {
            try {
                cacheService.unlock(LOCK_KEY, instanceId);
            } catch (Exception e) {
                log.warn("[B2B] Failed to release quote-expiry lock: {}", e.getMessage());
            }
        }
    }
}
