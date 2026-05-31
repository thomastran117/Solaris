package backend.services.intf.premium;

import backend.dtos.responses.premium.PremiumStatusResponse;

import java.util.UUID;

public interface PremiumService {

    PremiumStatusResponse getStatus(UUID userId);

    /** Creates a Stripe Checkout Session for the premium price. Returns the hosted URL. */
    String createCheckoutSession(UUID userId);

    /** Creates a Stripe Customer Portal session for managing/cancelling the subscription. Returns the hosted URL. */
    String createPortalSession(UUID userId);

    void handleSubscriptionUpdated(String stripeSubscriptionId);

    void handleSubscriptionDeleted(String stripeSubscriptionId);

    void handleInvoicePaymentFailed(String stripeSubscriptionId);
}
