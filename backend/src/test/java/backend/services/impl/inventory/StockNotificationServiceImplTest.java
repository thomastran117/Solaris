package backend.services.impl.inventory;

import backend.dtos.requests.inventory.SubscribeBackInStockRequest;
import backend.dtos.responses.inventory.StockNotificationResponse;
import backend.exceptions.http.ForbiddenException;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.StockNotification;
import backend.models.core.User;
import backend.models.enums.NotificationStatus;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.StockNotificationRepository;
import backend.repositories.UserRepository;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockNotificationServiceImplTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID OTHER_USER_ID = TestIds.uuid(2);
    private static final UUID PRODUCT_ID = TestIds.uuid(3);
    private static final UUID VARIANT_ID = TestIds.uuid(4);
    private static final UUID NOTIFICATION_ID = TestIds.uuid(5);

    private StockNotificationRepository notificationRepository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private UserRepository userRepository;
    private EmailService emailService;
    private StockNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(StockNotificationRepository.class);
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        service = new StockNotificationServiceImpl(
                notificationRepository, productRepository, variantRepository, userRepository, emailService, "https://shopwave.test");
        when(notificationRepository.save(any(StockNotification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void subscribe_existingNotificationIsReactivated() {
        Product product = product();
        StockNotification existing = notification(USER_ID);
        existing.setStatus(NotificationStatus.NOTIFIED);
        existing.setNotifiedAt(Instant.parse("2026-05-18T00:00:00Z"));

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(notificationRepository.findByUserIdAndProductIdAndVariantRef(USER_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(existing));

        SubscribeBackInStockRequest request = new SubscribeBackInStockRequest();
        request.setProductId(PRODUCT_ID);

        StockNotificationResponse response = service.subscribe(USER_ID, request);

        assertEquals("PENDING", response.status());
        assertEquals(NotificationStatus.PENDING, existing.getStatus());
        assertNull(existing.getNotifiedAt());
    }

    @Test
    void cancel_foreignNotificationThrowsForbidden() {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification(OTHER_USER_ID)));

        assertThrows(ForbiddenException.class, () -> service.cancel(USER_ID, NOTIFICATION_ID));
    }

    @Test
    void notifySubscribers_sendsEmailAndMarksNotificationAsSent() {
        StockNotification notification = notification(USER_ID);
        notification.setVariant(variant());
        notification.setVariantRef(VARIANT_ID);
        when(notificationRepository.findAllByProductIdAndVariantRefAndStatus(PRODUCT_ID, VARIANT_ID, NotificationStatus.PENDING))
                .thenReturn(List.of(notification));

        service.notifySubscribers(PRODUCT_ID, VARIANT_ID);

        verify(emailService).sendBackInStockEmail(
                "user@test.com",
                "Taylor",
                PRODUCT_ID,
                "Desk",
                VARIANT_ID,
                "Black / XL",
                "https://shopwave.test/products/" + PRODUCT_ID);
        assertEquals(NotificationStatus.NOTIFIED, notification.getStatus());
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@test.com");
        user.setFirstName("Taylor");
        return user;
    }

    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Desk");
        product.setPrice(BigDecimal.TEN);
        product.setCurrency("USD");
        return product;
    }

    private ProductVariant variant() {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setSku("DESK-BLACK");
        variant.setOption1("Black");
        variant.setOption2("XL");
        return variant;
    }

    private StockNotification notification(UUID userId) {
        StockNotification notification = new StockNotification();
        notification.setId(NOTIFICATION_ID);
        notification.setUser(user(userId));
        notification.setProduct(product());
        notification.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return notification;
    }
}
