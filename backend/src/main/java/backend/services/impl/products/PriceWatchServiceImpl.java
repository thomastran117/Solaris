package backend.services.impl.products;

import backend.dtos.responses.products.PriceWatcherResponse;
import backend.events.products.PriceDropAlertEvent;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.PriceWatcher;
import backend.repositories.PriceWatcherRepository;
import backend.repositories.ProductRepository;
import backend.repositories.UserRepository;
import backend.services.intf.products.PriceWatchService;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class PriceWatchServiceImpl implements PriceWatchService {

    private static final Logger log = LoggerFactory.getLogger(PriceWatchServiceImpl.class);

    private final PriceWatcherRepository priceWatcherRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final String frontendBaseUrl;

    public PriceWatchServiceImpl(
            PriceWatcherRepository priceWatcherRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            EmailService emailService,
            @Value("${app.email.verification-base-url}") String frontendBaseUrl) {
        this.priceWatcherRepository = priceWatcherRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    @Transactional
    public PriceWatcherResponse watchProduct(UUID userId, UUID productId) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        int currentPriceCents = toCents(product.getPrice());

        PriceWatcher watcher = priceWatcherRepository
                .findByUserIdAndProductId(userId, productId)
                .map(existing -> {
                    existing.setWatchPriceCents(currentPriceCents);
                    return existing;
                })
                .orElseGet(() -> {
                    PriceWatcher w = new PriceWatcher();
                    w.setUser(userRepository.getReferenceById(userId));
                    w.setProduct(product);
                    w.setWatchPriceCents(currentPriceCents);
                    return w;
                });

        return toResponse(priceWatcherRepository.save(watcher));
    }

    @Override
    @Transactional
    public void unwatchProduct(UUID userId, UUID productId) {
        priceWatcherRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PriceWatcherResponse> getWatchedProducts(UUID userId, Pageable pageable) {
        return priceWatcherRepository.findAllByUserId(userId, pageable).map(this::toResponse);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPriceDrop(PriceDropAlertEvent event) {
        try {
            int newPriceCents = toCents(event.newPrice());
            int oldPriceCents = toCents(event.oldPrice());
            String productUrl = frontendBaseUrl + "/products/" + event.productId();

            List<PriceWatcher> eligible = priceWatcherRepository
                    .findAllByProductIdAndWatchPriceCentsGreaterThan(event.productId(), newPriceCents);

            if (eligible.isEmpty()) return;

            String productName = eligible.get(0).getProduct().getName();

            for (PriceWatcher watcher : eligible) {
                try {
                    emailService.sendPriceDropEmail(
                            watcher.getUser().getEmail(),
                            watcher.getUser().getId(),
                            productName,
                            productUrl,
                            oldPriceCents,
                            newPriceCents);
                } catch (Exception e) {
                    log.error("Failed to send price-drop email for watcher id={}: {}",
                            watcher.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error processing price-drop notifications for product={}: {}",
                    event.productId(), e.getMessage());
        }
    }

    private PriceWatcherResponse toResponse(PriceWatcher w) {
        return new PriceWatcherResponse(
                w.getId(),
                w.getProduct().getId(),
                w.getProduct().getName(),
                w.getWatchPriceCents(),
                w.getCreatedAt());
    }

    private static int toCents(BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }
}
