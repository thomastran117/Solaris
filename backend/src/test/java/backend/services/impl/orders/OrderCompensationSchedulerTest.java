package backend.services.impl.orders;

import backend.models.core.Order;
import backend.models.core.OrderCompensation;
import backend.models.enums.CompensationStatus;
import backend.models.enums.OrderStatus;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderRepository;
import backend.services.intf.promotions.LoyaltyService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCompensationSchedulerTest {

    private OrderRepository orderRepository;
    private OrderCompensationRepository compensationRepository;
    private OrderServiceImpl orderService;
    private OrderCompensationScheduler scheduler;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        compensationRepository = mock(OrderCompensationRepository.class);
        orderService = mock(OrderServiceImpl.class);
        scheduler = new OrderCompensationScheduler(
                orderRepository,
                compensationRepository,
                orderService,
                mock(LoyaltyService.class));
        ReflectionTestUtils.setField(scheduler, "staleOrderMinutes", 20);
    }

    @Test
    void compensateStaleOrders_processesReservedAndFailedOrders() {
        Order reserved = order(TestIds.uuid(1), OrderStatus.RESERVED);
        Order failed = order(TestIds.uuid(2), OrderStatus.FAILED);

        when(orderRepository.findAllByStatusAndCompensatedFalseAndCreatedAtBefore(eq(OrderStatus.RESERVED), any(Instant.class)))
                .thenReturn(List.of(reserved));
        when(orderRepository.findAllByStatusAndCompensatedFalseAndCreatedAtBefore(eq(OrderStatus.FAILED), any(Instant.class)))
                .thenReturn(List.of(failed));

        scheduler.compensateStaleOrders();

        verify(orderService).compensateOrder(reserved);
        verify(orderService).compensateOrder(failed);
    }

    @Test
    void compensateStaleOrders_continuesWhenACompensationFails() {
        Order first = order(TestIds.uuid(3), OrderStatus.RESERVED);
        Order second = order(TestIds.uuid(4), OrderStatus.RESERVED);

        when(orderRepository.findAllByStatusAndCompensatedFalseAndCreatedAtBefore(eq(OrderStatus.RESERVED), any(Instant.class)))
                .thenReturn(List.of(first, second));
        when(orderRepository.findAllByStatusAndCompensatedFalseAndCreatedAtBefore(eq(OrderStatus.FAILED), any(Instant.class)))
                .thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(orderService).compensateOrder(first);

        scheduler.compensateStaleOrders();

        verify(orderService).compensateOrder(first);
        verify(orderService).compensateOrder(second);
    }

    @Test
    void retryFailedCompensations_retriesEachClaimableRecord() {
        OrderCompensation first = compensation(TestIds.uuid(5));
        OrderCompensation second = compensation(TestIds.uuid(6));
        when(compensationRepository.findAllByStatusAndAttemptsLessThan(CompensationStatus.FAILED, 5))
                .thenReturn(List.of(first, second));

        scheduler.retryFailedCompensations();

        verify(orderService).retryCompensation(first);
        verify(orderService).retryCompensation(second);
    }

    @Test
    void retryFailedCompensations_continuesAfterException() {
        OrderCompensation first = compensation(TestIds.uuid(7));
        OrderCompensation second = compensation(TestIds.uuid(8));
        when(compensationRepository.findAllByStatusAndAttemptsLessThan(CompensationStatus.FAILED, 5))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("boom")).when(orderService).retryCompensation(first);

        scheduler.retryFailedCompensations();

        verify(orderService).retryCompensation(first);
        verify(orderService).retryCompensation(second);
    }

    private Order order(java.util.UUID id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return order;
    }

    private OrderCompensation compensation(java.util.UUID id) {
        OrderCompensation compensation = new OrderCompensation();
        compensation.setId(id);
        compensation.setStatus(CompensationStatus.FAILED);
        compensation.setAttempts(1);
        return compensation;
    }
}
