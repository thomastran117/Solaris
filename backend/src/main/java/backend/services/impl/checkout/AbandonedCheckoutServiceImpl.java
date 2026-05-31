package backend.services.impl.checkout;

import backend.events.email.EmailEvent;
import backend.events.order.OrderReservationExpiredEvent;
import backend.models.core.AbandonedCartRecovery;
import backend.repositories.AbandonedCartRecoveryRepository;
import backend.services.intf.checkout.AbandonedCheckoutService;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class AbandonedCheckoutServiceImpl implements AbandonedCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(AbandonedCheckoutServiceImpl.class);

    private final AbandonedCartRecoveryRepository recoveryRepository;
    private final EmailService emailService;

    public AbandonedCheckoutServiceImpl(AbandonedCartRecoveryRepository recoveryRepository,
                                        EmailService emailService) {
        this.recoveryRepository = recoveryRepository;
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationExpired(OrderReservationExpiredEvent event) {
        handleExpiredReservation(event);
    }

    @Override
    public void handleExpiredReservation(OrderReservationExpiredEvent event) {
        LocalDate today = LocalDate.now();

        if (recoveryRepository.existsByUserIdAndSentDate(event.userId(), today)) {
            log.debug("Abandoned cart recovery already sent for userId={} on {}", event.userId(), today);
            return;
        }

        AbandonedCartRecovery recovery = new AbandonedCartRecovery();
        recovery.setUserId(event.userId());
        recovery.setOrderId(event.orderId());
        recovery.setSentDate(today);
        try {
            recoveryRepository.save(recovery);
        } catch (DataIntegrityViolationException e) {
            // Concurrent expiry for the same user on the same day — unique constraint enforces the rate limit.
            log.info("Concurrent abandoned cart recovery suppressed for userId={}", event.userId());
            return;
        }

        List<EmailEvent.AbandonedItem> items = event.items().stream()
                .map(d -> new EmailEvent.AbandonedItem(
                        d.productId(),
                        d.productName(),
                        d.thumbnailUrl(),
                        d.unitPrice().multiply(BigDecimal.valueOf(100)).longValue()))
                .toList();

        emailService.sendAbandonedCartEmail(
                event.userEmail(), event.userFirstName(), event.userId(), event.orderId(), items);
    }
}
