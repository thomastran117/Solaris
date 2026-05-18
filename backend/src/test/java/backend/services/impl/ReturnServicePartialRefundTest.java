package backend.services.impl;

import java.util.UUID;
import backend.testutil.TestIds;
import backend.dtos.responses.return_.ReturnResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.core.User;
import backend.models.enums.OrderStatus;
import backend.models.enums.RefundStatus;
import backend.models.enums.UserRole;
import backend.repositories.CompanyRepository;
import backend.repositories.CompanyReturnLocationRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.ReturnItemRepository;
import backend.repositories.ReturnRepository;
import backend.repositories.RiskAssessmentRepository;
import backend.repositories.UserRepository;
import backend.services.impl.returns.ReturnServiceImpl;
import backend.services.intf.ActivityEventPublisher;
import backend.services.intf.payments.PaymentService;
import backend.services.intf.pricing.RiskEngine;
import backend.configurations.environment.RiskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReturnServicePartialRefundTest {

    private ReturnRepository returnRepository;
    private OrderRepository orderRepository;
    private PaymentService paymentService;
    private UserRepository userRepository;
    private ReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        returnRepository = mock(ReturnRepository.class);
        orderRepository = mock(OrderRepository.class);
        paymentService = mock(PaymentService.class);
        userRepository = mock(UserRepository.class);

        service = new ReturnServiceImpl(
                returnRepository,
                mock(ReturnItemRepository.class),
                orderRepository,
                mock(OrderCompensationRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(LocationStockRepository.class),
                mock(InventoryAdjustmentRepository.class),
                mock(CompanyRepository.class),
                mock(CompanyReturnLocationRepository.class),
                userRepository,
                paymentService,
                mock(RiskEngine.class),
                mock(RiskAssessmentRepository.class),
                mock(RiskProperties.class),
                mock(ActivityEventPublisher.class));
    }

    @Test
    void issuePartialRefund_createsReturnAndFiresStripeRefund() throws Exception {
        User customer = makeUser(TestIds.uuid(1));
        Order order = makeOrder(10L, customer);
        User staff = makeStaffUser(2L);

        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(staff));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        PaymentService.RefundResult refundResult = new PaymentService.RefundResult("re_test123", 500L, "usd", "pending", "pi_test");
        when(paymentService.refundPayment(eq("pi_test"), anyLong())).thenReturn(refundResult);

        when(returnRepository.save(any())).thenAnswer(inv -> {
            var ret = inv.getArgument(0, backend.models.core.Return.class);
            ret.setId(99L);
            return ret;
        });

        ReturnResponse resp = service.issuePartialRefund(10L, 500L, "Damaged in transit", 2L);

        assertEquals(99L, resp.id());
        assertEquals(RefundStatus.PENDING.name(), resp.refundStatus());
        verify(paymentService).refundPayment(eq("pi_test"), anyLong());
    }

    @Test
    void issuePartialRefund_throwsWhenAmountZero() {
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeStaffUser(2L)));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(makeOrder(10L, makeUser(TestIds.uuid(1)))));
        assertThrows(BadRequestException.class,
                () -> service.issuePartialRefund(10L, 0L, null, 2L));
    }

    @Test
    void issuePartialRefund_throwsWhenOrderNotFound() {
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeStaffUser(2L)));
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.issuePartialRefund(99L, 500L, null, 2L));
    }

    @Test
    void issuePartialRefund_throwsWhenActorIsNotStaff() {
        when(userRepository.findById(TestIds.uuid(2))).thenReturn(Optional.of(makeUser(TestIds.uuid(2))));

        assertThrows(ForbiddenException.class,
                () -> service.issuePartialRefund(10L, 500L, null, 2L));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@test.com");
        u.setRole(UserRole.USER);
        return u;
    }

    private User makeStaffUser(long id) {
        User u = makeUser(id);
        u.setRole(UserRole.SUPPORT);
        return u;
    }

    private Order makeOrder(long id, User owner) {
        Order o = new Order();
        o.setId(id);
        o.setUser(owner);
        o.setTotalAmount(BigDecimal.TEN);
        o.setCurrency("USD");
        o.setStatus(OrderStatus.DELIVERED);
        o.setPaymentIntentId("pi_test");
        o.setItems(new ArrayList<>());
        o.setCouponDiscountAmount(BigDecimal.ZERO);
        o.setPromotionSavings(BigDecimal.ZERO);
        return o;
    }
}
