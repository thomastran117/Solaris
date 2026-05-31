import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FulfillmentActionBar from "./FulfillmentActionBar";
import type { CompanyOrder, OrderItemSummary } from "../../types/order";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../../api/companyOrders", () => ({
  companyOrdersApi: {
    pack: vi.fn(),
    ship: vi.fn(),
    markPickupReady: vi.fn(),
    deliver: vi.fn(),
    cancel: vi.fn(),
  },
}));

import { companyOrdersApi } from "../../api/companyOrders";
const mockPack = vi.mocked(companyOrdersApi.pack);
const mockShip = vi.mocked(companyOrdersApi.ship);
const mockPickupReady = vi.mocked(companyOrdersApi.markPickupReady);
const mockDeliver = vi.mocked(companyOrdersApi.deliver);
const mockCancel = vi.mocked(companyOrdersApi.cancel);

// ─── helpers ─────────────────────────────────────────────────────────────────

function makeItem(fulfillmentStatus: OrderItemSummary["fulfillmentStatus"] = "PACKED"): OrderItemSummary {
  return {
    id: "item-1", productId: null, productName: "Widget",
    variantId: null, variantTitle: null, variantSku: null,
    quantity: 1, unitPrice: 49.99, discountAmount: 0,
    fulfillmentStatus, fulfillmentMethod: "DELIVERY",
    fulfillmentLocationName: null, bundleName: null,
  };
}

function makeOrder(overrides: Partial<CompanyOrder> = {}): CompanyOrder {
  return {
    orderId: "order-1", buyerUserId: "user-1", orderStatus: "PAID",
    currency: "USD", companyItemsTotal: 49.99, items: [makeItem()],
    fulfillmentMethod: "DELIVERY", trackingNumber: null, carrier: null,
    shippedAt: null, deliveredAt: null, pickupLocationId: null,
    pickupLocationName: null, pickupReadyAt: null,
    shipRecipientName: null, shipStreet: null, shipStreet2: null,
    shipCity: null, shipState: null, shipPostalCode: null, shipCountry: null,
    createdAt: "2026-05-20T10:00:00Z",
    ...overrides,
  };
}

function renderBar(order: CompanyOrder, onRefresh = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <FulfillmentActionBar order={order} companyId="company-1" onRefresh={onRefresh} />
    </QueryClientProvider>
  );
  return { onRefresh };
}

beforeEach(() => {
  vi.clearAllMocks();
});

// ─── button visibility ────────────────────────────────────────────────────────

describe("FulfillmentActionBar — button visibility", () => {
  it("DELIVERY + PAID: shows Mark Packed only", () => {
    renderBar(makeOrder({ orderStatus: "PAID", fulfillmentMethod: "DELIVERY" }));
    expect(screen.getByRole("button", { name: /Mark Packed/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mark Shipped/i })).not.toBeInTheDocument();
  });

  it("DELIVERY + PACKED: shows Mark Shipped only", () => {
    renderBar(makeOrder({ orderStatus: "PACKED", fulfillmentMethod: "DELIVERY" }));
    expect(screen.getByRole("button", { name: /Mark Shipped/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mark Packed/i })).not.toBeInTheDocument();
  });

  it("DELIVERY + SHIPPED: shows Mark Delivered only", () => {
    renderBar(makeOrder({ orderStatus: "SHIPPED", fulfillmentMethod: "DELIVERY" }));
    expect(screen.getByRole("button", { name: /Mark Delivered/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mark Shipped/i })).not.toBeInTheDocument();
  });

  it("PICKUP + PAID: shows Mark Packed only", () => {
    renderBar(makeOrder({ orderStatus: "PAID", fulfillmentMethod: "PICKUP", items: [makeItem()] }));
    expect(screen.getByRole("button", { name: /Mark Packed/i })).toBeInTheDocument();
  });

  it("PICKUP + PACKED with PACKED items: shows Mark Pickup Ready", () => {
    renderBar(makeOrder({
      orderStatus: "PACKED", fulfillmentMethod: "PICKUP",
      items: [{ ...makeItem(), fulfillmentMethod: "PICKUP" }],
    }));
    expect(screen.getByRole("button", { name: /Mark Pickup Ready/i })).toBeInTheDocument();
  });

  it("PICKUP + PACKED with PICKUP_READY items: shows Mark Delivered (Collected)", () => {
    renderBar(makeOrder({
      orderStatus: "PACKED", fulfillmentMethod: "PICKUP",
      items: [{ ...makeItem("PICKUP_READY"), fulfillmentMethod: "PICKUP" }],
    }));
    expect(screen.getByRole("button", { name: /Mark Delivered \(Collected\)/i })).toBeInTheDocument();
  });
});

// ─── ship modal ──────────────────────────────────────────────────────────────

describe("FulfillmentActionBar — ship modal", () => {
  it("opens ship modal when Mark Shipped is clicked", async () => {
    renderBar(makeOrder({ orderStatus: "PACKED", fulfillmentMethod: "DELIVERY" }));
    await userEvent.click(screen.getByRole("button", { name: /Mark Shipped/i }));
    expect(screen.getByPlaceholderText(/Tracking number/i)).toBeInTheDocument();
  });

  it("calls onRefresh and closes modal on ship success", async () => {
    mockShip.mockResolvedValue({ data: makeOrder() } as never);
    const { onRefresh } = renderBar(makeOrder({ orderStatus: "PACKED", fulfillmentMethod: "DELIVERY" }));

    await userEvent.click(screen.getByRole("button", { name: /Mark Shipped/i }));
    await userEvent.type(screen.getByPlaceholderText(/Tracking number/i), "TRACK-123");
    await userEvent.click(screen.getByRole("button", { name: /Confirm Ship/i }));

    await waitFor(() => expect(onRefresh).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByPlaceholderText(/Tracking number/i)).not.toBeInTheDocument());
  });

  it("shows error and keeps modal open on ship failure", async () => {
    mockShip.mockRejectedValue(new Error("Network error"));
    renderBar(makeOrder({ orderStatus: "PACKED", fulfillmentMethod: "DELIVERY" }));

    await userEvent.click(screen.getByRole("button", { name: /Mark Shipped/i }));
    await userEvent.type(screen.getByPlaceholderText(/Tracking number/i), "TRACK-123");
    await userEvent.click(screen.getByRole("button", { name: /Confirm Ship/i }));

    // Error appears in both the action bar and inside the modal
    await waitFor(() =>
      expect(screen.getAllByText(/Failed to mark as shipped/i).length).toBeGreaterThan(0)
    );
    expect(screen.getByPlaceholderText(/Tracking number/i)).toBeInTheDocument();
  });
});

// ─── pack mutation ────────────────────────────────────────────────────────────

describe("FulfillmentActionBar — pack mutation", () => {
  it("calls onRefresh after pack succeeds", async () => {
    mockPack.mockResolvedValue({ data: makeOrder() } as never);
    const { onRefresh } = renderBar(makeOrder({ orderStatus: "PAID" }));

    await userEvent.click(screen.getByRole("button", { name: /Mark Packed/i }));

    await waitFor(() => expect(onRefresh).toHaveBeenCalled());
  });

  it("shows error when pack fails", async () => {
    mockPack.mockRejectedValue(new Error("Server error"));
    renderBar(makeOrder({ orderStatus: "PAID" }));

    await userEvent.click(screen.getByRole("button", { name: /Mark Packed/i }));

    await waitFor(() => expect(screen.getByText(/Failed to mark as packed/i)).toBeInTheDocument());
  });
});

// ─── pickup-ready mutation ────────────────────────────────────────────────────

describe("FulfillmentActionBar — pickup ready mutation", () => {
  it("calls onRefresh after markPickupReady succeeds", async () => {
    mockPickupReady.mockResolvedValue({ data: makeOrder() } as never);
    const { onRefresh } = renderBar(makeOrder({
      orderStatus: "PACKED", fulfillmentMethod: "PICKUP",
      items: [{ ...makeItem(), fulfillmentMethod: "PICKUP" }],
    }));

    await userEvent.click(screen.getByRole("button", { name: /Mark Pickup Ready/i }));

    await waitFor(() => expect(onRefresh).toHaveBeenCalled());
  });
});

// ─── cancel button visibility ─────────────────────────────────────────────────

describe("FulfillmentActionBar — cancel button visibility", () => {
  it("PAID: shows Cancel Order alongside primary action", () => {
    renderBar(makeOrder({ orderStatus: "PAID", fulfillmentMethod: "DELIVERY" }));
    expect(screen.getByRole("button", { name: /Cancel Order/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Mark Packed/i })).toBeInTheDocument();
  });

  it("PACKED: shows Cancel Order alongside primary action", () => {
    renderBar(makeOrder({ orderStatus: "PACKED", fulfillmentMethod: "DELIVERY" }));
    expect(screen.getByRole("button", { name: /Cancel Order/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Mark Shipped/i })).toBeInTheDocument();
  });

  it("RESERVED: shows Cancel Order even with no primary fulfillment action", () => {
    renderBar(makeOrder({ orderStatus: "RESERVED", fulfillmentMethod: "DELIVERY" }));
    expect(screen.getByRole("button", { name: /Cancel Order/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mark Packed/i })).not.toBeInTheDocument();
  });

  it("SHIPPED: does not show Cancel Order", () => {
    renderBar(makeOrder({ orderStatus: "SHIPPED", fulfillmentMethod: "DELIVERY" }));
    expect(screen.queryByRole("button", { name: /Cancel Order/i })).not.toBeInTheDocument();
  });

  it("DELIVERED: renders nothing at all", () => {
    const { container } = (() => {
      const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
      return render(
        <QueryClientProvider client={qc}>
          <FulfillmentActionBar order={makeOrder({ orderStatus: "DELIVERED" })} companyId="company-1" />
        </QueryClientProvider>
      );
    })();
    expect(container.firstChild).toBeNull();
  });
});

// ─── cancel modal ─────────────────────────────────────────────────────────────

describe("FulfillmentActionBar — cancel modal", () => {
  it("opens cancel modal when Cancel Order is clicked", async () => {
    renderBar(makeOrder({ orderStatus: "PAID" }));
    await userEvent.click(screen.getByRole("button", { name: /Cancel Order/i }));
    expect(screen.getByText(/Cancel this order\?/i)).toBeInTheDocument();
  });

  it("closes modal when Go back is clicked", async () => {
    renderBar(makeOrder({ orderStatus: "PAID" }));
    await userEvent.click(screen.getByRole("button", { name: /Cancel Order/i }));
    await userEvent.click(screen.getByRole("button", { name: /Go back/i }));
    expect(screen.queryByText(/Cancel this order\?/i)).not.toBeInTheDocument();
  });

  it("calls cancel API and onRefresh on confirmation", async () => {
    mockCancel.mockResolvedValue({ data: makeOrder({ orderStatus: "CANCELLED" }) } as never);
    const { onRefresh } = renderBar(makeOrder({ orderStatus: "PAID" }));

    await userEvent.click(screen.getByRole("button", { name: /Cancel Order/i }));
    await userEvent.click(screen.getByRole("button", { name: /Confirm cancel/i }));

    await waitFor(() => expect(mockCancel).toHaveBeenCalledWith("company-1", "order-1"));
    await waitFor(() => expect(onRefresh).toHaveBeenCalled());
  });

  it("shows error and keeps modal open on cancel failure", async () => {
    mockCancel.mockRejectedValue(new Error("Server error"));
    renderBar(makeOrder({ orderStatus: "PAID" }));

    await userEvent.click(screen.getByRole("button", { name: /Cancel Order/i }));
    await userEvent.click(screen.getByRole("button", { name: /Confirm cancel/i }));

    // Error appears in both the action bar and inside the modal
    await waitFor(() =>
      expect(screen.getAllByText(/Failed to cancel order/i).length).toBeGreaterThan(0)
    );
    expect(screen.getByText(/Cancel this order\?/i)).toBeInTheDocument();
  });
});
