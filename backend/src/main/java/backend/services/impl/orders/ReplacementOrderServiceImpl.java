package backend.services.impl.orders;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.issue.ResolveWithReplacementRequest;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.ProductVariant;
import backend.models.core.User;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.repositories.OrderRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.services.intf.orders.ReplacementOrderService;
import backend.utilities.SecurityUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReplacementOrderServiceImpl implements ReplacementOrderService {

    private static final Logger log = LoggerFactory.getLogger(ReplacementOrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    public ReplacementOrderServiceImpl(OrderRepository orderRepository,
                                       ProductVariantRepository variantRepository,
                                       UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OrderResponse createReplacement(UUID originalOrderId,
                                           ResolveWithReplacementRequest request,
                                           UUID actorUserId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + actorUserId));
        SecurityUtils.requireStaff(actor);

        Order original = orderRepository.findById(originalOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + originalOrderId));

        if (request.items().isEmpty()) {
            throw new BadRequestException("Replacement order must contain at least one item");
        }

        Order replacement = new Order();
        replacement.setUser(original.getUser());
        replacement.setTotalAmount(BigDecimal.ZERO);
        replacement.setCurrency(original.getCurrency());
        replacement.setStatus(OrderStatus.PAID);
        // replacementOfOrderId is a loose FK still typed Long in Order entity — not stored until entity migrates
        // replacement.setReplacementOfOrderId(originalOrderId);
        replacement.setFulfillmentNote("Replacement for order #" + originalOrderId + ", authorised by staff #" + actorUserId);

        List<OrderItem> items = new ArrayList<>();
        for (ResolveWithReplacementRequest.ReplacementItem ri : request.items()) {
            ProductVariant variant = variantRepository.findById(ri.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + ri.variantId()));

            OrderItem item = new OrderItem();
            item.setOrder(replacement);
            item.setProduct(variant.getProduct());
            item.setVariant(variant);
            item.setVariantTitle(buildVariantTitle(variant));
            item.setVariantSku(variant.getSku());
            item.setQuantity(ri.quantity());
            item.setUnitPrice(BigDecimal.ZERO);
            item.setDiscountAmount(BigDecimal.ZERO);
            item.setPromotionSavings(BigDecimal.ZERO);
            item.setProductName(variant.getProduct().getName());

            // A replacement ships a real physical unit, so it must consume inventory — otherwise
            // fulfillment can oversell. Use the same atomic conditional decrement (WHERE stock >= qty)
            // as paid orders. If stock is insufficient, mark the line BACKORDERED rather than blocking
            // the staff-authorised replacement (mirrors the subscription-renewal path). The decrement
            // runs inside this @Transactional method, so a later failure rolls it back.
            int decremented = variantRepository.decrementStock(variant.getId(), ri.quantity());
            item.setFulfillmentStatus(decremented == 0 ? FulfillmentStatus.BACKORDERED : FulfillmentStatus.PENDING);
            items.add(item);
        }
        replacement.setItems(items);

        orderRepository.save(replacement);
        log.info("Replacement order {} created for original {} by staff {}", replacement.getId(), originalOrderId, actorUserId);
        return toResponse(replacement);
    }

    private String buildVariantTitle(ProductVariant v) {
        StringBuilder sb = new StringBuilder();
        if (v.getOption1() != null) sb.append(v.getOption1());
        if (v.getOption2() != null) { if (sb.length() > 0) sb.append(" / "); sb.append(v.getOption2()); }
        if (v.getOption3() != null) { if (sb.length() > 0) sb.append(" / "); sb.append(v.getOption3()); }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getId(),
                        i.getProduct() != null ? i.getProduct().getId() : null,
                        i.getProductName(),
                        i.getVariant() != null ? i.getVariant().getId() : null,
                        i.getVariantTitle(),
                        i.getVariantSku(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        null,
                        null,
                        i.getFulfillmentStatus(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        i.getDiscountAmount(),
                        i.getTaxAmount(),
                        i.getFulfillmentMethod()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                itemResponses,
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                null,
                null,
                order.getCouponCode(),
                order.getCouponDiscountAmount(),
                order.getTaxAmount(),
                order.getTaxableAmount(),
                order.getTaxRate(),
                order.getTaxSource() != null ? order.getTaxSource().name() : null,
                order.getFulfillmentMethod() != null ? order.getFulfillmentMethod().name() : FulfillmentMethod.DELIVERY.name(),
                order.getPickupLocationName(),
                order.getPickupReadyAt(),
                order.getPreferredDeliveryDate(),
                order.getPreferredDeliveryWindow() != null ? order.getPreferredDeliveryWindow().name() : null,
                order.getDeliverySlotStatus() != null ? order.getDeliverySlotStatus().name() : null,
                order.getShipRecipientName(),
                order.getShipStreet(),
                order.getShipStreet2(),
                order.getShipCity(),
                order.getShipState(),
                order.getShipPostalCode(),
                order.getShipCountry(),
                order.getShipPhoneNumber(),
                order.getTrackingNumber(),
                order.getCarrier(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getReturnedAt(),
                order.getFulfillmentNote(),
                order.getRefundedAmountCents(),
                order.getAssignedDriverId(),
                order.getShippingRateId(),
                order.getShippingCarrier(),
                order.getShippingServiceCode(),
                order.getShippingServiceName(),
                order.getShippingRateCurrency(),
                order.getShippingEstimatedDays(),
                order.getShippingCostCents(),
                order.getShippingRateQuotedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
