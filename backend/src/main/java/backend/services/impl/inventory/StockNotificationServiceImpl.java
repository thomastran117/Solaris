package backend.services.impl.inventory;

import java.util.UUID;
import backend.dtos.requests.inventory.SubscribeBackInStockRequest;
import backend.dtos.responses.inventory.StockNotificationResponse;
import backend.events.inventory.StockRestoredEvent;
import backend.events.notification.NotificationEvent;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.StockNotification;
import backend.models.enums.NotificationStatus;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.StockNotificationRepository;
import backend.repositories.UserRepository;
import backend.kafka.producers.NotificationEventPublisher;
import backend.services.intf.inventory.StockNotificationService;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StockNotificationServiceImpl implements StockNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StockNotificationServiceImpl.class);

    private final StockNotificationRepository notificationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationEventPublisher notificationEventPublisher;
    private final String frontendBaseUrl;

    public StockNotificationServiceImpl(
            StockNotificationRepository notificationRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            UserRepository userRepository,
            EmailService emailService,
            NotificationEventPublisher notificationEventPublisher,
            @Value("${app.email.verification-base-url}") String frontendBaseUrl) {
        this.notificationRepository = notificationRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.notificationEventPublisher = notificationEventPublisher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    @Transactional
    public StockNotificationResponse subscribe(UUID userId, SubscribeBackInStockRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        ProductVariant variant = null;
        UUID variantRef = null;
        if (request.getVariantId() != null) {
            variant = variantRepository.findByIdAndProductId(request.getVariantId(), product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + request.getVariantId()));
            variantRef = variant.getId();
        }

        UUID finalVariantRef = variantRef;
        ProductVariant finalVariant = variant;

        StockNotification notification = notificationRepository
                .findByUserIdAndProductIdAndVariantRef(userId, product.getId(), variantRef)
                .map(existing -> {
                    existing.setStatus(NotificationStatus.PENDING);
                    existing.setNotifiedAt(null);
                    return existing;
                })
                .orElseGet(() -> {
                    StockNotification n = new StockNotification();
                    n.setUser(userRepository.getReferenceById(userId));
                    n.setProduct(product);
                    n.setVariant(finalVariant);
                    n.setVariantRef(finalVariantRef);
                    n.setStatus(NotificationStatus.PENDING);
                    return n;
                });

        return toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public void cancel(UUID userId, UUID notificationId) {
        StockNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!notification.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this notification");
        }

        notification.setStatus(NotificationStatus.CANCELLED);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockNotificationResponse> listByUser(UUID userId) {
        return notificationRepository.findAllByUserIdAndStatusNot(userId, NotificationStatus.CANCELLED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void notifySubscribers(UUID productId, UUID variantRef) {
        List<StockNotification> pending = notificationRepository
                .findAllByProductIdAndVariantRefAndStatus(productId, variantRef, NotificationStatus.PENDING);

        if (pending.isEmpty()) return;

        for (StockNotification notification : pending) {
            try {
                String toEmail = notification.getUser().getEmail();
                String firstName = notification.getUser().getFirstName();
                String productName = notification.getProduct().getName();
                UUID variantId = notification.getVariant() != null ? notification.getVariant().getId() : null;
                String variantTitle = notification.getVariant() != null
                        ? buildVariantTitle(notification.getVariant())
                        : null;
                String productUrl = frontendBaseUrl + "/products/" + productId;

                emailService.sendBackInStockEmail(toEmail, firstName, productId, productName,
                        variantId, variantTitle, productUrl);

                notificationEventPublisher.publish(new NotificationEvent.BackInStock(
                        notification.getUser().getId(), productId, productName,
                        variantId, variantTitle, productUrl));

                notification.setStatus(NotificationStatus.NOTIFIED);
                notification.setNotifiedAt(Instant.now());
                notificationRepository.save(notification);

            } catch (Exception e) {
                log.error("Failed to send back-in-stock notification id={}: {}", notification.getId(), e.getMessage());
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStockRestored(StockRestoredEvent event) {
        try {
            notifySubscribers(event.productId(), event.variantId());
        } catch (Exception e) {
            log.error("Error processing back-in-stock notifications for product={} variantRef={}: {}",
                    event.productId(), event.variantId(), e.getMessage());
        }
    }

    private String buildVariantTitle(ProductVariant variant) {
        return Stream.of(variant.getOption1(), variant.getOption2(), variant.getOption3())
                .filter(o -> o != null && !o.isBlank())
                .collect(Collectors.joining(" / "));
    }

    private StockNotificationResponse toResponse(StockNotification n) {
        String variantTitle = n.getVariant() != null ? buildVariantTitle(n.getVariant()) : null;
        return new StockNotificationResponse(
                n.getId(),
                n.getProduct().getId(),
                n.getProduct().getName(),
                n.getVariant() != null ? n.getVariant().getId() : null,
                variantTitle,
                n.getStatus().name(),
                n.getCreatedAt()
        );
    }
}
