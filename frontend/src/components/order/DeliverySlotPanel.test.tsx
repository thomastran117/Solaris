import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import DeliverySlotPanel from "./DeliverySlotPanel";
import type { Order } from "../../types/order";

vi.mock("../../api/orders", () => ({
  ordersApi: { setDeliverySlot: vi.fn() },
}));

import { ordersApi } from "../../api/orders";
const mockSet = vi.mocked(ordersApi.setDeliverySlot);

function isoDay(offset: number): string {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + offset);
  return d.toISOString().slice(0, 10);
}

function makeOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: "order-1", userId: "user-1", items: [], totalAmount: 49.99,
    currency: "USD", status: "PAID", fulfillmentMethod: "DELIVERY",
    trackingNumber: null, carrier: null, shippedAt: null, deliveredAt: null,
    returnedAt: null, fulfillmentNote: null,
    shipRecipientName: null, shipStreet: null, shipStreet2: null,
    shipCity: null, shipState: null, shipPostalCode: null, shipCountry: null,
    shipPhoneNumber: null, pickupLocationName: null, pickupReadyAt: null,
    preferredDeliveryDate: null, preferredDeliveryWindow: null, deliverySlotStatus: null,
    couponCode: null, couponDiscountAmount: 0, refundedAmountCents: 0,
    assignedDriverId: null,
    shippingRateId: null, shippingCarrier: null, shippingServiceCode: null,
    shippingServiceName: null, shippingRateCurrency: null, shippingEstimatedDays: null,
    shippingCostCents: 0, shippingRateQuotedAt: null,
    createdAt: "2026-05-20T10:00:00Z", updatedAt: "2026-05-20T10:00:00Z",
    ...overrides,
  };
}

function renderPanel(order: Order) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <DeliverySlotPanel order={order} />
    </QueryClientProvider>
  );
}

beforeEach(() => vi.clearAllMocks());

describe("DeliverySlotPanel", () => {
  it("renders the request form for a PAID delivery order", () => {
    renderPanel(makeOrder({ status: "PAID" }));
    expect(screen.getByLabelText(/Preferred date/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Request slot/i })).toBeInTheDocument();
  });

  it("submits a valid slot to the API", async () => {
    mockSet.mockResolvedValue({ data: makeOrder() } as never);
    renderPanel(makeOrder({ status: "PAID" }));

    const date = isoDay(3);
    fireEvent.change(screen.getByLabelText(/Preferred date/i), { target: { value: date } });
    await userEvent.selectOptions(screen.getByLabelText(/Time window/i), "AFTERNOON");
    await userEvent.click(screen.getByRole("button", { name: /Request slot/i }));

    await waitFor(() =>
      expect(mockSet).toHaveBeenCalledWith("order-1", {
        preferredDeliveryDate: date,
        preferredDeliveryWindow: "AFTERNOON",
      })
    );
  });

  it("blocks submission and does not call the API when no date is chosen", async () => {
    renderPanel(makeOrder({ status: "PAID" }));

    // Submit with the date field left empty — validation must stop the request.
    await userEvent.click(screen.getByRole("button", { name: /Request slot/i }));

    await waitFor(() => expect(screen.getByText(/choose a delivery date/i)).toBeInTheDocument());
    expect(mockSet).not.toHaveBeenCalled();
  });

  it("shows the existing slot and status badge when one is set", () => {
    renderPanel(makeOrder({
      status: "PAID",
      preferredDeliveryDate: isoDay(4),
      preferredDeliveryWindow: "MORNING",
      deliverySlotStatus: "CONFIRMED",
    }));
    expect(screen.getByText(/Confirmed/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Update slot/i })).toBeInTheDocument();
  });

  it("hides the form once the order is past PAID and no slot exists", () => {
    renderPanel(makeOrder({ status: "SHIPPED" }));
    expect(screen.queryByRole("button", { name: /Request slot/i })).not.toBeInTheDocument();
    expect(screen.getByText(/can only be requested while/i)).toBeInTheDocument();
  });
});
