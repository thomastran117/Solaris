package backend.services.impl.premium;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.responses.premium.PremiumStatusResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.User;
import backend.models.enums.UserTier;
import backend.repositories.UserRepository;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.payments.PaymentService.CustomerResult;
import backend.services.intf.premium.PremiumService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PremiumServiceImpl implements PremiumService {

    private static final Logger log = LoggerFactory.getLogger(PremiumServiceImpl.class);

    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final EnvironmentSetting env;

    public PremiumServiceImpl(UserRepository userRepository,
                              PaymentService paymentService,
                              EnvironmentSetting env) {
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.env = env;
    }

    @Override
    @Transactional(readOnly = true)
    public PremiumStatusResponse getStatus(UUID userId) {
        User user = requireUser(userId);
        return new PremiumStatusResponse(user.getTier(), user.getPremiumExpiresAt());
    }

    @Override
    @Transactional
    public String createCheckoutSession(UUID userId) {
        User user = requireUser(userId);
        String customerId = ensureStripeCustomer(user);
        EnvironmentSetting.Stripe.Premium cfg = env.getStripe().getPremium();
        return paymentService.createCheckoutSession(
                customerId,
                cfg.getPriceId(),
                cfg.getCheckoutSuccessUrl(),
                cfg.getCheckoutCancelUrl()
        );
    }

    @Override
    @Transactional
    public String createPortalSession(UUID userId) {
        User user = requireUser(userId);
        if (user.getStripeCustomerId() == null || user.getStripeCustomerId().isBlank()) {
            throw new BadRequestException("No billing account found. Please upgrade to Premium first.");
        }
        return paymentService.createPortalSession(
                user.getStripeCustomerId(),
                env.getStripe().getPremium().getPortalReturnUrl()
        );
    }

    /** Called by the controller with the already-extracted event metadata. */
    @Transactional
    public void handleCheckoutCompletedFromEvent(String stripeCustomerId,
                                                 String stripeSubscriptionId,
                                                 String currentPeriodEndEpoch) {
        User user = userRepository.findByStripeCustomerId(stripeCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("No user found for Stripe customer: " + stripeCustomerId));

        Instant expiresAt = currentPeriodEndEpoch != null && !currentPeriodEndEpoch.isBlank()
                ? Instant.ofEpochSecond(Long.parseLong(currentPeriodEndEpoch))
                : null;

        user.setTier(UserTier.PREMIUM);
        user.setPremiumStripeSubscriptionId(stripeSubscriptionId);
        user.setPremiumExpiresAt(expiresAt);
        userRepository.save(user);
        log.info("User {} upgraded to PREMIUM via checkout session (sub={})", user.getId(), stripeSubscriptionId);
    }

    @Override
    @Transactional
    public void handleSubscriptionUpdated(String stripeSubscriptionId) {
        userRepository.findByPremiumStripeSubscriptionId(stripeSubscriptionId).ifPresentOrElse(user -> {
            // Re-sync from Stripe
            PaymentService.SubscriptionResult sub = paymentService.retrieveSubscription(stripeSubscriptionId);
            boolean active = "active".equalsIgnoreCase(sub.status()) || "trialing".equalsIgnoreCase(sub.status());
            if (active) {
                user.setTier(UserTier.PREMIUM);
                user.setPremiumExpiresAt(sub.currentPeriodEnd());
            } else if ("canceled".equalsIgnoreCase(sub.status()) || "unpaid".equalsIgnoreCase(sub.status())) {
                downgradeTier(user);
            }
            userRepository.save(user);
            log.info("Premium subscription {} updated — user {} tier={}", stripeSubscriptionId, user.getId(), user.getTier());
        }, () -> log.debug("handleSubscriptionUpdated: no user found for sub {}", stripeSubscriptionId));
    }

    @Override
    @Transactional
    public void handleSubscriptionDeleted(String stripeSubscriptionId) {
        userRepository.findByPremiumStripeSubscriptionId(stripeSubscriptionId).ifPresentOrElse(user -> {
            downgradeTier(user);
            userRepository.save(user);
            log.info("Premium subscription {} deleted — user {} downgraded to FREE", stripeSubscriptionId, user.getId());
        }, () -> log.debug("handleSubscriptionDeleted: no user found for sub {}", stripeSubscriptionId));
    }

    @Override
    public void handleInvoicePaymentFailed(String stripeSubscriptionId) {
        log.warn("Premium invoice payment failed for subscription {}. Stripe will retry; downgrade happens on subscription.deleted.", stripeSubscriptionId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private String ensureStripeCustomer(User user) {
        if (user.getStripeCustomerId() != null && !user.getStripeCustomerId().isBlank()) {
            return user.getStripeCustomerId();
        }
        String name = (user.getFirstName() != null ? user.getFirstName() : "")
                + (user.getLastName() != null ? " " + user.getLastName() : "");
        CustomerResult c = paymentService.createCustomer(
                user.getEmail(), name.trim(), java.util.Map.of("user_id", user.getId().toString()));
        user.setStripeCustomerId(c.id());
        userRepository.save(user);
        return c.id();
    }

    private void downgradeTier(User user) {
        user.setTier(UserTier.FREE);
        user.setPremiumStripeSubscriptionId(null);
        user.setPremiumExpiresAt(null);
    }
}
