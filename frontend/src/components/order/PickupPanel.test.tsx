import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import PickupPanel from "./PickupPanel";
import type { CompanyOrder, OrderItemSummary } from "../../types/order";

function makeItem(fulfillmentStatus: OrderItemSummary["fulfillmentStatus"] = "PACKED"): OrderItemSummary {
  return {
    id: "item-1",
    productId: null,
    productName: "Widget",
    variantId: null,
    variantTitle: null,
    variantSku: null,
    quantity: 1,
    unitPrice: 49.99,
    discountAmount: 0,
    fulfillmentStatus,
    fulfillmentMethod: "PICKUP",
    fulfillmentLocationName: null,
    bundleName: null,
  };
}

function makeOrder(overrides: Partial<CompanyOrder> = {}): CompanyOrder {
  return {
    orderId: "order-1",
    buyerUserId: "user-1",
    orderStatus: "PACKED",
    currency: "USD",
    companyItemsTotal: 49.99,
    items: [makeItem()],
    fulfillmentMethod: "PICKUP",
    trackingNumber: null,
    carrier: null,
    shippedAt: null,
    deliveredAt: null,
    pickupLocationId: null,
    pickupLocationName: "Downtown Store",
    pickupReadyAt: null,
    shipRecipientName: null,
    shipStreet: null,
    shipStreet2: null,
    shipCity: null,
    shipState: null,
    shipPostalCode: null,
    shipCountry: null,
    createdAt: "2026-05-20T10:00:00Z",
    ...overrides,
  };
}

describe("PickupPanel", () => {
  it("shows 'Preparing your order' when items are PACKED", () => {
    render(<PickupPanel order={makeOrder()} />);
    expect(screen.getByText("Preparing your order")).toBeInTheDocument();
  });

  it("shows 'Ready for pickup!' when items are PICKUP_READY", () => {
    render(<PickupPanel order={makeOrder({ items: [makeItem("PICKUP_READY")] })} />);
    expect(screen.getByText("Ready for pickup!")).toBeInTheDocument();
  });

  it("shows pickup location name in all states", () => {
    render(<PickupPanel order={makeOrder({ pickupLocationName: "City Centre" })} />);
    expect(screen.getByText("City Centre")).toBeInTheDocument();
  });

  it("shows pickupReadyAt timestamp when available", () => {
    render(
      <PickupPanel
        order={makeOrder({
          items: [makeItem("PICKUP_READY")],
          pickupReadyAt: "2026-05-20T14:00:00Z",
        })}
      />
    );
    expect(screen.getByText(/Ready since/)).toBeInTheDocument();
  });

  it("does not show pickupReadyAt when null", () => {
    render(<PickupPanel order={makeOrder({ pickupReadyAt: null })} />);
    expect(screen.queryByText(/Ready since/)).not.toBeInTheDocument();
  });

  it("shows 'Collected' when order is DELIVERED", () => {
    render(<PickupPanel order={makeOrder({ orderStatus: "DELIVERED" })} />);
    expect(screen.getByText("Collected")).toBeInTheDocument();
  });
});
