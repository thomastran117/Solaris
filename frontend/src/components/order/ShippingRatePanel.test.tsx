import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import ShippingRatePanel from "./ShippingRatePanel";
import type { Order, ShippingRate } from "../../types/order";

vi.mock("../../api/orders", () => ({
  ordersApi: { getShippingRates: vi.fn(), confirmShippingRate: vi.fn() },
}));

import { ordersApi } from "../../api/orders";
const mockGet = vi.mocked(ordersApi.getShippingRates);
const mockConfirm = vi.mocked(ordersApi.confirmShippingRate);

function makeOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: "order-1", userId: "user-1", items: [], totalAmount: 49.99,
    currency: "USD", status: "RESERVED", fulfillmentMethod: "DELIVERY",
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
    createdAt: "2026-06-20T10:00:00Z", updatedAt: "2026-06-20T10:00:00Z",
    ...overrides,
  };
}

const RATES: ShippingRate[] = [
  { rateId: "rate_1", carrier: "USPS", serviceName: "Priority", serviceCode: "Priority", estimatedDays: 2, totalCents: 799, currency: "USD" },
  { rateId: "rate_2", carrier: "UPS", serviceName: "Ground", serviceCode: "Ground", estimatedDays: 5, totalCents: 599, currency: "USD" },
];

function renderPanel(order: Order) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <ShippingRatePanel order={order} />
    </QueryClientProvider>
  );
}

beforeEach(() => vi.clearAllMocks());

describe("ShippingRatePanel", () => {
  it("renders the rate options returned by the API", async () => {
    mockGet.mockResolvedValue({ data: RATES } as never);
    renderPanel(makeOrder());

    await waitFor(() => expect(screen.getByText(/USPS · Priority/)).toBeInTheDocument());
    expect(screen.getByText(/UPS · Ground/)).toBeInTheDocument();
    expect(screen.getByText("USD 7.99")).toBeInTheDocument();
    expect(screen.getByText("USD 5.99")).toBeInTheDocument();
  });

  it("confirms the selected rate via the API", async () => {
    mockGet.mockResolvedValue({ data: RATES } as never);
    mockConfirm.mockResolvedValue({ data: makeOrder({ shippingRateId: "rate_2", shippingCostCents: 599 }) } as never);
    renderPanel(makeOrder());

    await waitFor(() => expect(screen.getByText(/UPS · Ground/)).toBeInTheDocument());
    await userEvent.click(screen.getByRole("radio", { name: /UPS · Ground/ }));
    await userEvent.click(screen.getByRole("button", { name: /Confirm shipping/i }));

    await waitFor(() => expect(mockConfirm).toHaveBeenCalledWith("order-1", "rate_2"));
  });

  it("shows a fallback message when no rates are available", async () => {
    mockGet.mockResolvedValue({ data: [] } as never);
    renderPanel(makeOrder());

    await waitFor(() =>
      expect(screen.getByText(/standard shipping will be applied/i)).toBeInTheDocument()
    );
    expect(mockConfirm).not.toHaveBeenCalled();
  });

  it("shows an error state when the rate lookup fails", async () => {
    mockGet.mockRejectedValue(new Error("boom"));
    renderPanel(makeOrder());

    await waitFor(() =>
      expect(screen.getByText(/Couldn't load shipping options/i)).toBeInTheDocument()
    );
  });
});
