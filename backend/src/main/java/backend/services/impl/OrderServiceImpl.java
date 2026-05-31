package backend.services.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.order.CreateOrderRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.core.OrderCompensation;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompensationStatus;
import backend.models.enums.CompensationType;
import backend.models.enums.OrderStatus;
import backend.models.enums.ProductStatus;
import backend.repositories.OrderCompensationRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.OrderService;
import backend.services.intf.PaymentService;
import backend.services.intf.PaymentService.PaymentIntentResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final String LOCK_PREFIX = "lock:product:";
    private static final long LOCK_TTL_SECONDS = 10;
    private static final int LOCK_RETRY_ATTEMPTS = 5;
    private static final long LOCK_RETRY_DELAY_MS = 100;
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalAmount");

    private final OrderRepository orderRepository;
    private final OrderCompensationRepository compensationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final CacheService cacheService;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderCompensationRepository compensationRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            PaymentService paymentService,
            CacheService cacheService) {
        this.orderRepository = orderRepository;
        this.compensationRepository = compensationRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.cacheService = cacheService;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(long userId, CreateOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<CreateOrderRequest.OrderItemRequest> itemRequests = request.getItems();
        List<Long> productIds = itemRequests.stream()
                .map(CreateOrderRequest.OrderItemRequest::getProductId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, Integer> quantityMap = new HashMap<>();
        for (CreateOrderRequest.OrderItemRequest item : itemRequests) {
            quantityMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        String lockToken = UUID.randomUUID().toString();
        List<String> acquiredLocks = new ArrayList<>();
        List<long[]> decrementedProducts = new ArrayList<>();

        try {
            acquireLocks(productIds, lockToken, acquiredLocks);

            List<Product> products = productRepository.findAllById(productIds);
            if (products.size() != productIds.size()) {
                throw new ResourceNotFoundException("One or more products not found");
            }

            Map<Long, Product> productMap = new HashMap<>();
            for (Product p : products) {
                productMap.put(p.getId(), p);
            }

            for (Product product : products) {
                if (product.getStatus() != ProductStatus.ACTIVE) {
                    throw new BadRequestException("Product '" + product.getName() + "' is not available for purchase");
                }
            }

            List<OrderItem> orderItems = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (Long productId : productIds) {
                Product product = productMap.get(productId);
                int qty = quantityMap.get(productId);

                int updated = productRepository.decrementStock(product.getId(), qty);
                if (updated == 0) {
                    safeRestoreStock(decrementedProducts);
                    throw new ConflictException("Insufficient stock for product '" + product.getName() + "'");
                }

                decrementedProducts.add(new long[]{product.getId(), qty});

                OrderItem item = new OrderItem();
                item.setProduct(product);
                item.setQuantity(qty);
                item.setUnitPrice(product.getPrice());
                item.setProductName(product.getName());
                orderItems.add(item);

                totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(qty)));
            }

            String currency = request.getCurrency() != null ? request.getCurrency().toLowerCase() : "usd";
            long amountInCents = totalAmount.multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            Order order = new Order();
            order.setUser(user);
            order.setTotalAmount(totalAmount);
            order.setCurrency(currency);
            order.setStatus(OrderStatus.PENDING);

            for (OrderItem item : orderItems) {
                item.setOrder(order);
            }
            order.setItems(orderItems);

            order = orderRepository.save(order);

            PaymentIntentResult paymentIntent;
            try {
                paymentIntent = paymentService.createPaymentIntent(
                        amountInCents,
                        currency,
                        null,
                        Map.of("user_id", String.valueOf(userId), "order_id", String.valueOf(order.getId()))
                );
            } catch (Exception e) {
                order.setStatus(OrderStatus.FAILED);
                order.setFailureReason("Payment intent creation failed: " + e.getMessage());
                orderRepository.save(order);
                scheduleStockCompensation(order, decrementedProducts);
                throw e;
            }

            order.setPaymentIntentId(paymentIntent.id());
            order.setPaymentClientSecret(paymentIntent.clientSecret());

            return toResponse(orderRepository.save(order));
        } catch (ConflictException | ResourceNotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            safeRestoreStock(decrementedProducts);
            throw e;
        } finally {
            releaseLocks(acquiredLocks, lockToken);
        }
    }

    @Override
    public OrderResponse getOrder(long orderId, long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return toResponse(order);
    }

    @Override
    public PagedResponse<OrderResponse> getOrders(long userId, OrderStatus status, int page, int size, String sort, String direction) {
        if (size > 50) size = 50;

        String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortField));

        if (status != null) {
            return new PagedResponse<>(
                    orderRepository.findAllByUserIdAndStatus(userId, status, pageable).map(this::toResponse)
            );
        }
        return new PagedResponse<>(
                orderRepository.findAllByUserId(userId, pageable).map(this::toResponse)
        );
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(long orderId, long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Only pending orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);

        if (order.getPaymentIntentId() != null) {
            try {
                paymentService.cancelPaymentIntent(order.getPaymentIntentId());
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancelled payment intent: " + order.getPaymentIntentId(), CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Failed to cancel payment intent {} for order {}: {}",
                        order.getPaymentIntentId(), order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Failed to cancel payment intent: " + order.getPaymentIntentId(), CompensationStatus.FAILED, e.getMessage());
            }
        }

        for (OrderItem item : order.getItems()) {
            try {
                productRepository.restoreStock(item.getProduct().getId(), item.getQuantity());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Restored " + item.getQuantity() + " units for product " + item.getProduct().getId(), CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Failed to restore stock for product {} on order {}: {}",
                        item.getProduct().getId(), order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Failed to restore " + item.getQuantity() + " units for product " + item.getProduct().getId(), CompensationStatus.FAILED, e.getMessage());
            }
        }

        order.setCompensated(true);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public void handlePaymentSuccess(String paymentIntentId) {
        orderRepository.findByPaymentIntentId(paymentIntentId).ifPresent(order -> {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
        });
    }

    @Override
    @Transactional
    public void handlePaymentFailure(String paymentIntentId) {
        orderRepository.findByPaymentIntentId(paymentIntentId).ifPresent(order -> {
            if (order.isCompensated()) return;

            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason("Payment failed via webhook");

            for (OrderItem item : order.getItems()) {
                try {
                    productRepository.restoreStock(item.getProduct().getId(), item.getQuantity());
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            "Restored " + item.getQuantity() + " units for product " + item.getProduct().getId(), CompensationStatus.COMPLETED);
                } catch (Exception e) {
                    log.error("Failed to restore stock for product {} on failed order {}: {}",
                            item.getProduct().getId(), order.getId(), e.getMessage());
                    recordCompensation(order, CompensationType.STOCK_RESTORE,
                            "Failed to restore " + item.getQuantity() + " units for product " + item.getProduct().getId(), CompensationStatus.FAILED, e.getMessage());
                }
            }

            order.setCompensated(true);
            orderRepository.save(order);
        });
    }

    /**
     * Compensates a single failed/stale order. Called by the scheduler for orders
     * that were not successfully compensated inline. Each step is individually
     * wrapped so a failure in one does not prevent the others from executing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateOrder(Order order) {
        if (order.isCompensated()) return;

        log.info("Compensating order {} (status={})", order.getId(), order.getStatus());

        for (OrderItem item : order.getItems()) {
            try {
                productRepository.restoreStock(item.getProduct().getId(), item.getQuantity());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Restored " + item.getQuantity() + " units for product " + item.getProduct().getId(), CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Scheduled compensation: failed to restore stock for product {} on order {}: {}",
                        item.getProduct().getId(), order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Restore " + item.getQuantity() + " units for product " + item.getProduct().getId(), CompensationStatus.FAILED, e.getMessage());
            }
        }

        if (order.getPaymentIntentId() != null && order.getStatus() != OrderStatus.CANCELLED) {
            try {
                paymentService.cancelPaymentIntent(order.getPaymentIntentId());
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancelled payment intent: " + order.getPaymentIntentId(), CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Scheduled compensation: failed to cancel payment {} for order {}: {}",
                        order.getPaymentIntentId(), order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.PAYMENT_CANCEL,
                        "Cancel payment intent: " + order.getPaymentIntentId(), CompensationStatus.FAILED, e.getMessage());
            }
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason("Compensated by scheduler — stale pending order");
        }
        order.setCompensated(true);
        orderRepository.save(order);
    }

    /**
     * Retries a single previously failed compensation record. Called by the scheduler
     * to ensure eventual consistency on items that failed their first compensation attempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryCompensation(OrderCompensation compensation) {
        compensation.setAttempts(compensation.getAttempts() + 1);

        try {
            switch (compensation.getType()) {
                case STOCK_RESTORE -> {
                    String detail = compensation.getDetail();
                    long productId = extractProductIdFromDetail(detail);
                    int quantity = extractQuantityFromDetail(detail);
                    if (productId > 0 && quantity > 0) {
                        productRepository.restoreStock(productId, quantity);
                    }
                }
                case PAYMENT_CANCEL -> {
                    String intentId = extractIntentIdFromDetail(compensation.getDetail());
                    if (intentId != null) {
                        paymentService.cancelPaymentIntent(intentId);
                    }
                }
                case PAYMENT_REFUND -> {
                    String intentId = extractIntentIdFromDetail(compensation.getDetail());
                    if (intentId != null) {
                        paymentService.refundPayment(intentId, null);
                    }
                }
            }
            compensation.setStatus(CompensationStatus.COMPLETED);
            compensation.setCompletedAt(Instant.now());
            compensation.setErrorMessage(null);
        } catch (Exception e) {
            log.error("Compensation retry failed for id={} type={}: {}",
                    compensation.getId(), compensation.getType(), e.getMessage());
            compensation.setErrorMessage(e.getMessage());
        }

        compensationRepository.save(compensation);
    }

    private void scheduleStockCompensation(Order order, List<long[]> decrementedProducts) {
        for (long[] entry : decrementedProducts) {
            try {
                productRepository.restoreStock(entry[0], (int) entry[1]);
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Restored " + entry[1] + " units for product " + entry[0], CompensationStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Inline stock compensation failed for product {} on order {}: {}",
                        entry[0], order.getId(), e.getMessage());
                recordCompensation(order, CompensationType.STOCK_RESTORE,
                        "Restore " + entry[1] + " units for product " + entry[0], CompensationStatus.FAILED, e.getMessage());
            }
        }
    }

    private void safeRestoreStock(List<long[]> decrementedProducts) {
        for (long[] entry : decrementedProducts) {
            try {
                productRepository.restoreStock(entry[0], (int) entry[1]);
            } catch (Exception e) {
                log.error("Emergency stock restore failed for product {}: {}", entry[0], e.getMessage());
            }
        }
    }

    private void recordCompensation(Order order, CompensationType type, String detail, CompensationStatus status) {
        recordCompensation(order, type, detail, status, null);
    }

    private void recordCompensation(Order order, CompensationType type, String detail, CompensationStatus status, String errorMessage) {
        OrderCompensation comp = new OrderCompensation();
        comp.setOrder(order);
        comp.setType(type);
        comp.setDetail(detail);
        comp.setStatus(status);
        comp.setErrorMessage(errorMessage);
        comp.setAttempts(1);
        if (status == CompensationStatus.COMPLETED) {
            comp.setCompletedAt(Instant.now());
        }
        compensationRepository.save(comp);
    }

    private void acquireLocks(List<Long> sortedProductIds, String lockToken, List<String> acquiredLocks) {
        for (Long productId : sortedProductIds) {
            String lockKey = LOCK_PREFIX + productId;
            boolean acquired = false;

            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(lockKey, lockToken, LOCK_TTL_SECONDS)) {
                    acquiredLocks.add(lockKey);
                    acquired = true;
                    break;
                }
                try {
                    Thread.sleep(LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Order processing interrupted, please try again");
                }
            }

            if (!acquired) {
                releaseLocks(acquiredLocks, lockToken);
                throw new ConflictException("Product is currently being purchased by another user, please try again shortly");
            }
        }
    }

    private void releaseLocks(List<String> lockKeys, String lockToken) {
        for (String lockKey : lockKeys) {
            try {
                cacheService.unlock(lockKey, lockToken);
            } catch (Exception e) {
                log.error("Failed to release lock {}: {}", lockKey, e.getMessage());
            }
        }
    }

    private static long extractProductIdFromDetail(String detail) {
        try {
            int idx = detail.lastIndexOf("product ");
            if (idx >= 0) return Long.parseLong(detail.substring(idx + 8).trim());
        } catch (Exception ignored) {}
        return -1;
    }

    private static int extractQuantityFromDetail(String detail) {
        try {
            int startIdx = detail.indexOf("Restore ") >= 0 ? detail.indexOf("Restore ") + 8 : detail.indexOf("Restored ") + 9;
            int endIdx = detail.indexOf(" units");
            if (startIdx > 0 && endIdx > startIdx) return Integer.parseInt(detail.substring(startIdx, endIdx).trim());
        } catch (Exception ignored) {}
        return -1;
    }

    private static String extractIntentIdFromDetail(String detail) {
        if (detail == null) return null;
        int idx = detail.lastIndexOf(": ");
        if (idx >= 0) return detail.substring(idx + 2).trim();
        idx = detail.lastIndexOf("intent: ");
        if (idx >= 0) return detail.substring(idx + 8).trim();
        return null;
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                items,
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getPaymentIntentId(),
                order.getPaymentClientSecret(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
