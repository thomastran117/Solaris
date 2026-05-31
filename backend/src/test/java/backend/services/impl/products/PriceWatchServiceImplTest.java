package backend.services.impl.products;

import backend.dtos.responses.products.PriceWatcherResponse;
import backend.events.products.PriceDropAlertEvent;
import backend.models.core.PriceWatcher;
import backend.models.core.Product;
import backend.models.core.User;
import backend.repositories.PriceWatcherRepository;
import backend.repositories.ProductRepository;
import backend.repositories.UserRepository;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PriceWatchServiceImplTest {

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID PRODUCT_ID = TestIds.uuid(2);
    private static final UUID WATCHER_ID = TestIds.uuid(3);

    private PriceWatcherRepository priceWatcherRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private EmailService emailService;
    private PriceWatchServiceImpl service;

    @BeforeEach
    void setUp() {
        priceWatcherRepository = mock(PriceWatcherRepository.class);
        productRepository      = mock(ProductRepository.class);
        userRepository         = mock(UserRepository.class);
        emailService           = mock(EmailService.class);
        service = new PriceWatchServiceImpl(
                priceWatcherRepository, productRepository, userRepository,
                emailService, "https://shopwave.test");
        when(priceWatcherRepository.save(any(PriceWatcher.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void watchProduct_createsNewWatcher() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(BigDecimal.valueOf(49.99))));
        when(priceWatcherRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());

        PriceWatcherResponse response = service.watchProduct(USER_ID, PRODUCT_ID);

        assertEquals(PRODUCT_ID, response.productId());
        assertEquals(4999, response.watchPriceCents());
        verify(priceWatcherRepository).save(any(PriceWatcher.class));
    }

    @Test
    void watchProduct_existingWatcher_updatesPrice() {
        PriceWatcher existing = watcher(3000);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product(BigDecimal.valueOf(49.99))));
        when(priceWatcherRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(existing));

        service.watchProduct(USER_ID, PRODUCT_ID);

        assertEquals(4999, existing.getWatchPriceCents());
        verify(priceWatcherRepository).save(existing);
    }

    @Test
    void unwatchProduct_delegatesDelete() {
        service.unwatchProduct(USER_ID, PRODUCT_ID);
        verify(priceWatcherRepository).deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    void getWatchedProducts_returnsMappedPage() {
        PriceWatcher w = watcher(2000);
        when(priceWatcherRepository.findAllByUserId(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(w)));

        var page = service.getWatchedProducts(USER_ID, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals(2000, page.getContent().get(0).watchPriceCents());
    }

    @Test
    void onPriceDrop_sendsEmailToEligibleWatchers() {
        PriceWatcher w = watcher(5000);
        when(priceWatcherRepository.findAllByProductIdAndWatchPriceCentsGreaterThan(PRODUCT_ID, 3999))
                .thenReturn(List.of(w));

        service.onPriceDrop(new PriceDropAlertEvent(
                PRODUCT_ID, BigDecimal.valueOf(50.00), BigDecimal.valueOf(39.99)));

        verify(emailService).sendPriceDropEmail(
                "user@test.com",
                USER_ID,
                "Desk",
                "https://shopwave.test/products/" + PRODUCT_ID,
                5000,
                3999);
    }

    @Test
    void onPriceDrop_skipsWatcherAtExactNewPrice() {
        // watchPriceCents == newPriceCents: strict > means no alert
        when(priceWatcherRepository.findAllByProductIdAndWatchPriceCentsGreaterThan(PRODUCT_ID, 3999))
                .thenReturn(List.of());

        service.onPriceDrop(new PriceDropAlertEvent(
                PRODUCT_ID, BigDecimal.valueOf(50.00), BigDecimal.valueOf(39.99)));

        verifyNoInteractions(emailService);
    }

    @Test
    void onPriceDrop_noEligibleWatchers_doesNothing() {
        when(priceWatcherRepository.findAllByProductIdAndWatchPriceCentsGreaterThan(PRODUCT_ID, 2999))
                .thenReturn(List.of());

        service.onPriceDrop(new PriceDropAlertEvent(
                PRODUCT_ID, BigDecimal.valueOf(40.00), BigDecimal.valueOf(29.99)));

        verifyNoInteractions(emailService);
    }

    // --- helpers ---

    private User user() {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail("user@test.com");
        return u;
    }

    private Product product(BigDecimal price) {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setName("Desk");
        p.setPrice(price);
        p.setCurrency("USD");
        return p;
    }

    private PriceWatcher watcher(int watchPriceCents) {
        PriceWatcher w = new PriceWatcher();
        w.setId(WATCHER_ID);
        w.setUser(user());
        w.setProduct(product(BigDecimal.valueOf(watchPriceCents / 100.0)));
        w.setWatchPriceCents(watchPriceCents);
        w.setCreatedAt(Instant.parse("2026-05-31T00:00:00Z"));
        return w;
    }
}
