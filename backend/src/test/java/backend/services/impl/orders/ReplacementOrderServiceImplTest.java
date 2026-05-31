package backend.services.impl.orders;

import backend.dtos.requests.issue.ResolveWithReplacementRequest;
import backend.dtos.responses.order.OrderResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.User;
import backend.models.enums.OrderStatus;
import backend.models.enums.UserRole;
import backend.repositories.OrderRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReplacementOrderServiceImplTest {

    private OrderRepository orderRepository;
    private ProductVariantRepository variantRepository;
    private UserRepository userRepository;
    private ReplacementOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository  = mock(OrderRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        userRepository   = mock(UserRepository.class);
        service = new ReplacementOrderServiceImpl(orderRepository, variantRepository, userRepository);
    }

    @Test
    void createReplacement_setsReplacementOfOrderIdAndZeroTotal() {
        User customer = makeUser(TestIds.uuid(1));
        User staff    = makeStaffUser(TestIds.uuid(2));
        Order original = makeOrder(TestIds.uuid(10), customer);
        ProductVariant variant = makeVariant(TestIds.uuid(5));

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(orderRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(variantRepository.findById(TestIds.uuid(5))).thenReturn(Optional.of(variant));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(TestIds.uuid(99));
            return o;
        });

        ResolveWithReplacementRequest req = new ResolveWithReplacementRequest(
                List.of(new ResolveWithReplacementRequest.ReplacementItem(TestIds.uuid(5), 1)),
                "123 Main St", "Springfield", "US", "12345");

        OrderResponse resp = service.createReplacement(TestIds.uuid(10), req, TestIds.uuid(2));

        assertEquals(TestIds.uuid(99), resp.getId());
        assertEquals(BigDecimal.ZERO, resp.getTotalAmount());
        assertEquals(OrderStatus.PAID.name(), resp.getStatus());
    }

    @Test
    void createReplacement_throwsWhenNoItems() {
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeStaffUser(TestIds.uuid(2))));
        Order original = makeOrder(TestIds.uuid(10), makeUser(TestIds.uuid(1)));
        when(orderRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));

        ResolveWithReplacementRequest req = new ResolveWithReplacementRequest(
                List.of(), "123 Main St", "Springfield", "US", "12345");

        assertThrows(BadRequestException.class, () -> service.createReplacement(TestIds.uuid(10), req, TestIds.uuid(2)));
    }

    @Test
    void createReplacement_throwsWhenVariantNotFound() {
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeStaffUser(TestIds.uuid(2))));
        Order original = makeOrder(TestIds.uuid(10), makeUser(TestIds.uuid(1)));
        when(orderRepository.findById(TestIds.uuid(10))).thenReturn(Optional.of(original));
        when(variantRepository.findById(TestIds.uuid(999))).thenReturn(Optional.empty());

        ResolveWithReplacementRequest req = new ResolveWithReplacementRequest(
                List.of(new ResolveWithReplacementRequest.ReplacementItem(TestIds.uuid(999), 1)),
                "123 Main St", "Springfield", "US", "12345");

        assertThrows(ResourceNotFoundException.class, () -> service.createReplacement(TestIds.uuid(10), req, TestIds.uuid(2)));
    }

    @Test
    void createReplacement_throwsWhenOriginalOrderNotFound() {
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeStaffUser(TestIds.uuid(2))));
        when(orderRepository.findById(TestIds.uuid(99))).thenReturn(Optional.empty());

        ResolveWithReplacementRequest req = new ResolveWithReplacementRequest(
                List.of(new ResolveWithReplacementRequest.ReplacementItem(TestIds.uuid(1), 1)),
                "123 Main St", "Springfield", "US", "12345");

        assertThrows(ResourceNotFoundException.class, () -> service.createReplacement(TestIds.uuid(99), req, TestIds.uuid(2)));
    }

    @Test
    void createReplacement_throwsWhenActorIsNotStaff() {
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeUser(TestIds.uuid(2))));

        ResolveWithReplacementRequest req = new ResolveWithReplacementRequest(
                List.of(new ResolveWithReplacementRequest.ReplacementItem(TestIds.uuid(1), 1)),
                "123 Main St", "Springfield", "US", "12345");

        assertThrows(ForbiddenException.class, () -> service.createReplacement(TestIds.uuid(10), req, TestIds.uuid(2)));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setRole(UserRole.USER);
        return u;
    }

    private User makeStaffUser(UUID id) {
        User u = makeUser(id);
        u.setRole(UserRole.SUPPORT);
        return u;
    }

    private Order makeOrder(UUID id, User owner) {
        Order o = new Order();
        o.setId(id);
        o.setUser(owner);
        o.setTotalAmount(BigDecimal.TEN);
        o.setCurrency("USD");
        o.setStatus(OrderStatus.DELIVERED);
        o.setItems(new ArrayList<>());
        o.setCouponDiscountAmount(BigDecimal.ZERO);
        o.setPromotionSavings(BigDecimal.ZERO);
        return o;
    }

    private ProductVariant makeVariant(UUID id) {
        Product product = new Product();
        product.setId(TestIds.uuid(1));
        product.setName("Test Product");

        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setProduct(product);
        v.setSku("SKU-" + id);
        v.setPrice(BigDecimal.TEN);
        return v;
    }
}
