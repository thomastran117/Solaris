package backend.controllers.impl.premium;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.premium.CheckoutSessionResponse;
import backend.dtos.responses.premium.PortalSessionResponse;
import backend.dtos.responses.premium.PremiumStatusResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.CacheService;
import backend.services.intf.payments.PaymentService;
import backend.services.impl.premium.PremiumServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/premium")
public class PremiumController {

    private static final long STRIPE_EVENT_DEDUP_TTL_SECONDS = 31L * 24 * 60 * 60;

    private final PremiumServiceImpl premiumService;
    private final PaymentService paymentService;
    private final CacheService cacheService;

    public PremiumController(PremiumServiceImpl premiumService,
                             PaymentService paymentService,
                             CacheService cacheService) {
        this.premiumService = premiumService;
        this.paymentService = paymentService;
        this.cacheService = cacheService;
    }

    @GetMapping("/status")
    @RequireAuth
    public ResponseEntity<PremiumStatusResponse> getStatus() {
        try {
            return ResponseEntity.ok(premiumService.getStatus(resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/checkout")
    @RequireAuth
    public ResponseEntity<CheckoutSessionResponse> createCheckout() {
        try {
            String url = premiumService.createCheckoutSession(resolveUserId());
            return ResponseEntity.ok(new CheckoutSessionResponse(url));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/portal")
    @RequireAuth
    public ResponseEntity<PortalSessionResponse> createPortal() {
        try {
            String url = premiumService.createPortalSession(resolveUserId());
            return ResponseEntity.ok(new PortalSessionResponse(url));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        PaymentService.WebhookEvent event = paymentService.constructPremiumWebhookEvent(payload, sigHeader);

        if (event.eventId() != null && !event.eventId().isBlank()) {
            String dedupKey = "stripe:premium:event:" + event.eventId();
            boolean firstDelivery = cacheService.tryLock(dedupKey, "1", STRIPE_EVENT_DEDUP_TTL_SECONDS);
            if (!firstDelivery) {
                return ResponseEntity.ok().build();
            }
        }

        switch (event.type()) {
            case "checkout.session.completed" -> {
                String customerId    = event.metadata().get("customerId");
                String subscriptionId = event.metadata().get("subscriptionId");
                if (customerId != null && subscriptionId != null) {
                    premiumService.handleCheckoutCompletedFromEvent(customerId, subscriptionId, null);
                }
            }
            case "customer.subscription.updated" -> {
                String subId = event.metadata().get("subscriptionId");
                if (subId != null) {
                    premiumService.handleSubscriptionUpdated(subId);
                }
            }
            case "customer.subscription.deleted" -> {
                String subId = event.metadata().get("subscriptionId");
                if (subId == null) subId = event.objectId();
                if (subId != null) {
                    premiumService.handleSubscriptionDeleted(subId);
                }
            }
            case "invoice.payment_failed" -> {
                String subId = event.metadata().get("subscriptionId");
                if (subId != null) {
                    premiumService.handleInvoicePaymentFailed(subId);
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
