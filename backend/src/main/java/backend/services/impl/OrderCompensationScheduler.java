package backend.services.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import backend.models.core.Order;
import backend.models.core.OrderCompensation;
import backend.models.enums.CompensationStatus;
import backend.models.enums.OrderStatus;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Periodically compensates stale orders and retries failed compensation records.
 *
 * <ul>
 *   <li><b>Stale orders</b>: PENDING orders older than 30 minutes that were never compensated
 *       — their stock is restored and payment intent cancelled.</li>
 *   <li><b>Failed compensations</b>: individual compensation records that failed on first
 *       attempt are retried up to 5 times with each scheduled run.</li>
 * </ul>
 */
@Component
public class OrderCompensationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderCompensationScheduler.class);

    private static final int MAX_COMPENSATION_ATTEMPTS = 5;
    private static final int STALE_ORDER_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderCompensationRepository compensationRepository;
    private final OrderServiceImpl orderService;

    public OrderCompensationScheduler(
            OrderRepository orderRepository,
            OrderCompensationRepository compensationRepository,
            OrderServiceImpl orderService) {
        this.orderRepository = orderRepository;
        this.compensationRepository = compensationRepository;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.order.compensation.interval-ms:300000}")
    public void compensateStaleOrders() {
        Instant cutoff = Instant.now().minus(STALE_ORDER_MINUTES, ChronoUnit.MINUTES);
        List<Order> staleOrders = orderRepository.findAllByStatusAndCompensatedFalseAndCreatedAtBefore(
                OrderStatus.PENDING, cutoff);

        if (!staleOrders.isEmpty()) {
            log.info("Found {} stale pending orders to compensate", staleOrders.size());
        }

        for (Order order : staleOrders) {
            try {
                orderService.compensateOrder(order);
            } catch (Exception e) {
                log.error("Failed to compensate stale order {}: {}", order.getId(), e.getMessage());
            }
        }

        List<Order> failedUncompensated = orderRepository.findAllByStatusAndCompensatedFalseAndCreatedAtBefore(
                OrderStatus.FAILED, cutoff);

        for (Order order : failedUncompensated) {
            try {
                orderService.compensateOrder(order);
            } catch (Exception e) {
                log.error("Failed to compensate failed order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.order.compensation.retry-interval-ms:600000}")
    public void retryFailedCompensations() {
        List<OrderCompensation> failed = compensationRepository.findAllByStatusAndAttemptsLessThan(
                CompensationStatus.FAILED, MAX_COMPENSATION_ATTEMPTS);

        if (!failed.isEmpty()) {
            log.info("Retrying {} failed compensation records", failed.size());
        }

        for (OrderCompensation compensation : failed) {
            try {
                orderService.retryCompensation(compensation);
            } catch (Exception e) {
                log.error("Compensation retry failed for record {}: {}", compensation.getId(), e.getMessage());
            }
        }
    }
}
