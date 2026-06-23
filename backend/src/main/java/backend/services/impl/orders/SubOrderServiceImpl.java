package backend.services.impl.orders;

import java.util.UUID;
import backend.dtos.requests.order.CancelSubOrderRequest;
import backend.dtos.requests.order.ShipSubOrderRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.CommissionRecordResponse;
import backend.dtos.responses.order.OrderItemResponse;
import backend.dtos.responses.order.SubOrderResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CommissionRecord;
import backend.models.core.MarketplaceVendor;
import backend.models.core.OrderItem;
import backend.models.core.SubOrder;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.SubOrderStatus;
import backend.repositories.CommissionRecordRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.OrderItemRepository;
import backend.repositories.SubOrderRepository;
import backend.services.intf.orders.SubOrderService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SubOrderServiceImpl implements SubOrderService {

    private final SubOrderRepository subOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CommissionRecordRepository commissionRecordRepository;
    private final MarketplaceVendorRepository marketplaceVendorRepository;

    public SubOrderServiceImpl(
            SubOrderRepository subOrderRepository,
            OrderItemRepository orderItemRepository,
            CommissionRecordRepository commissionRecordRepository,
            MarketplaceVendorRepository marketplaceVendorRepository) {
        this.subOrderRepository = subOrderRepository;
        this.orderItemRepository = orderItemRepository;
        this.commissionRecordRepository = commissionRecordRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SubOrderResponse> listVendorSubOrders(UUID marketplaceVendorId, SubOrderStatus status, int page, int size, UUID ownerId) {
        assertVendorOwnership(marketplaceVendorId, ownerId);
        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SubOrder> results = status != null
                ? subOrderRepository.findByMarketplaceVendorIdAndStatus(marketplaceVendorId, status, pageable)
                : subOrderRepository.findByMarketplaceVendorId(marketplaceVendorId, pageable);
        return new PagedResponse<>(results.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public SubOrderResponse getSubOrder(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId) {
        return toResponse(resolveVendorSubOrder(subOrderId, marketplaceVendorId, ownerId));
    }

    @Override
    @Transactional
    public SubOrderResponse markPacked(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId) {
        SubOrder subOrder = resolveVendorSubOrder(subOrderId, marketplaceVendorId, ownerId);
        if (subOrder.getStatus() != SubOrderStatus.PENDING) {
            throw new BadRequestException("Sub-order must be PENDING to mark as packed");
        }
        subOrder.setStatus(SubOrderStatus.PACKED);
        subOrder.setPackedAt(Instant.now());

        orderItemRepository.findAllBySubOrderId(subOrderId).forEach(item -> {
            if (item.getFulfillmentStatus() == FulfillmentStatus.PENDING) {
                item.setFulfillmentStatus(FulfillmentStatus.PACKED);
                orderItemRepository.save(item);
            }
        });

        return toResponse(subOrderRepository.save(subOrder));
    }

    @Override
    @Transactional
    public SubOrderResponse markShipped(UUID subOrderId, UUID marketplaceVendorId, ShipSubOrderRequest request, UUID ownerId) {
        SubOrder subOrder = resolveVendorSubOrder(subOrderId, marketplaceVendorId, ownerId);
        if (subOrder.getStatus() != SubOrderStatus.PACKED) {
            throw new BadRequestException("Sub-order must be PACKED before marking as shipped");
        }
        subOrder.setStatus(SubOrderStatus.SHIPPED);
        subOrder.setShippedAt(Instant.now());
        subOrder.setTrackingNumber(request.getTrackingNumber());
        subOrder.setCarrier(request.getCarrier());
        if (request.getFulfillmentNote() != null) {
            subOrder.setFulfillmentNote(request.getFulfillmentNote());
        }

        orderItemRepository.findAllBySubOrderId(subOrderId).forEach(item -> {
            if (item.getFulfillmentStatus() == FulfillmentStatus.PACKED) {
                item.setFulfillmentStatus(FulfillmentStatus.SHIPPED);
                orderItemRepository.save(item);
            }
        });

        return toResponse(subOrderRepository.save(subOrder));
    }

    @Override
    @Transactional
    public SubOrderResponse markDelivered(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId) {
        SubOrder subOrder = resolveVendorSubOrder(subOrderId, marketplaceVendorId, ownerId);
        if (subOrder.getStatus() != SubOrderStatus.SHIPPED) {
            throw new BadRequestException("Sub-order must be SHIPPED before marking as delivered");
        }
        subOrder.setStatus(SubOrderStatus.DELIVERED);
        subOrder.setDeliveredAt(Instant.now());

        orderItemRepository.findAllBySubOrderId(subOrderId).forEach(item -> {
            if (item.getFulfillmentStatus() == FulfillmentStatus.SHIPPED) {
                item.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
                orderItemRepository.save(item);
            }
        });

        return toResponse(subOrderRepository.save(subOrder));
    }

    @Override
    @Transactional
    public SubOrderResponse cancelSubOrder(UUID subOrderId, UUID marketplaceVendorId, CancelSubOrderRequest request, UUID ownerId) {
        SubOrder subOrder = resolveVendorSubOrder(subOrderId, marketplaceVendorId, ownerId);
        if (subOrder.getStatus() == SubOrderStatus.SHIPPED
                || subOrder.getStatus() == SubOrderStatus.DELIVERED
                || subOrder.getStatus() == SubOrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot cancel a sub-order in status " + subOrder.getStatus());
        }
        subOrder.setStatus(SubOrderStatus.CANCELLED);
        subOrder.setCancelledAt(Instant.now());
        subOrder.setCancellationReason(request.getReason());

        orderItemRepository.findAllBySubOrderId(subOrderId).forEach(item -> {
            if (item.getFulfillmentStatus() == FulfillmentStatus.PENDING
                    || item.getFulfillmentStatus() == FulfillmentStatus.BACKORDERED
                    || item.getFulfillmentStatus() == FulfillmentStatus.PREORDERED
                    || item.getFulfillmentStatus() == FulfillmentStatus.PACKED) {
                item.setFulfillmentStatus(FulfillmentStatus.CANCELLED);
                orderItemRepository.save(item);
            }
        });

        return toResponse(subOrderRepository.save(subOrder));
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionRecordResponse getCommissionRecord(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId) {
        resolveVendorSubOrder(subOrderId, marketplaceVendorId, ownerId);
        CommissionRecord record = commissionRecordRepository.findBySubOrderId(subOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Commission record not yet available for this sub-order"));
        return toCommissionResponse(record);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SubOrder resolveVendorSubOrder(UUID subOrderId, UUID marketplaceVendorId, UUID ownerId) {
        assertVendorOwnership(marketplaceVendorId, ownerId);
        return subOrderRepository.findByIdAndMarketplaceVendorId(subOrderId, marketplaceVendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Sub-order not found"));
    }

    private MarketplaceVendor assertVendorOwnership(UUID marketplaceVendorId, UUID ownerId) {
        MarketplaceVendor vendor = marketplaceVendorRepository.findById(marketplaceVendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
        if (!vendor.getVendorCompany().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You do not own this vendor company");
        }
        return vendor;
    }

    private SubOrderResponse toResponse(SubOrder s) {
        List<OrderItem> items = orderItemRepository.findAllBySubOrderId(s.getId());
        List<OrderItemResponse> itemResponses = items.stream().map(this::toItemResponse).toList();
        return new SubOrderResponse(
                s.getId(),
                s.getOrder().getId(),
                s.getMarketplaceVendor().getId(),
                null, // marketplaceId is a loose FK still typed Long in SubOrder entity
                s.getMarketplaceVendor().getVendorCompany().getName(),
                s.getStatus().name(),
                s.getSubtotal(),
                s.getTotalAmount(),
                s.getCurrency(),
                s.getCommissionAmount(),
                s.getNetVendorAmount(),
                s.getTrackingNumber(),
                s.getCarrier(),
                s.getFulfillmentNote(),
                s.getCancellationReason(),
                s.getPaidAt(),
                s.getPackedAt(),
                s.getShippedAt(),
                s.getDeliveredAt(),
                s.getCancelledAt(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                itemResponses
        );
    }

    private OrderItemResponse toItemResponse(OrderItem i) {
        return new OrderItemResponse(
                i.getId(),
                i.getProduct() != null ? i.getProduct().getId() : null,
                i.getProductName(),
                i.getVariant() != null ? i.getVariant().getId() : null,
                i.getVariantTitle(),
                i.getVariantSku(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getFulfillmentLocation() != null ? i.getFulfillmentLocation().getId() : null,
                i.getFulfillmentLocationName(),
                i.getFulfillmentStatus(),
                i.getBundle() != null ? i.getBundle().getId() : null,
                i.getBundleName(),
                i.getKit() != null ? i.getKit().getId() : null,
                i.getKitName(),
                null,
                i.getDiscountAmount(),
                i.getTaxAmount(),
                i.getFulfillmentMethod()
        );
    }

    private CommissionRecordResponse toCommissionResponse(CommissionRecord r) {
        return new CommissionRecordResponse(
                r.getId(),
                r.getSubOrder().getId(),
                r.getVendorId(),
                r.getMarketplaceId(),
                r.getCommissionRate(),
                r.getGrossAmount(),
                r.getCommissionAmount(),
                r.getNetVendorAmount(),
                r.getCurrency(),
                r.getComputedAt()
        );
    }
}
