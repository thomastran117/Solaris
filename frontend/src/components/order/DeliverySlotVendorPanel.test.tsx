import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import DeliverySlotVendorPanel from "./DeliverySlotVendorPanel";
import type { CompanyOrder } from "../../types/order";

vi.mock("../../api/companyOrders", () => ({
  companyOrdersApi: {
    confirmDeliverySlot: vi.fn(),
    markDeliverySlotUnavailable: vi.fn(),
  },
}));

import { companyOrdersApi } from "../../api/companyOrders";
const mockConfirm = vi.mocked(companyOrdersApi.confirmDeliverySlot);
const mockUnavailable = vi.mocked(companyOrdersApi.markDeliverySlotUnavailable);

function makeOrder(overrides: Partial<CompanyOrder> = {}): CompanyOrder {
  return {
    orderId: "order-1", buyerUserId: "user-1", orderStatus: "PAID",
    currency: "USD", companyItemsTotal: 49.99, items: [],
    fulfillmentMethod: "DELIVERY", trackingNumber: null, carrier: null,
    shippedAt: null, deliveredAt: null, pickupLocationId: null,
    pickupLocationName: null, pickupReadyAt: null,
    preferredDeliveryDate: "2026-06-20", preferredDeliveryWindow: "MORNING",
    deliverySlotStatus: "REQUESTED",
    shipRecipientName: null, shipStreet: null, shipStreet2: null,
    shipCity: null, shipState: null, shipPostalCode: null, shipCountry: null,
    assignedDriverId: null, createdAt: "2026-05-20T10:00:00Z",
    ...overrides,
  };
}

function renderPanel(order: CompanyOrder, onRefresh = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <DeliverySlotVendorPanel order={order} companyId="company-1" onRefresh={onRefresh} />
    </QueryClientProvider>
  );
  return { onRefresh };
}

beforeEach(() => vi.clearAllMocks());

describe("DeliverySlotVendorPanel", () => {
  it("renders nothing when the order has no requested slot", () => {
    const { container } = (() => {
      const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
      return render(
        <QueryClientProvider client={qc}>
          <DeliverySlotVendorPanel order={makeOrder({ deliverySlotStatus: null })} companyId="company-1" />
        </QueryClientProvider>
      );
    })();
    expect(container.firstChild).toBeNull();
  });

  it("shows Confirm and Mark Unavailable for a REQUESTED slot", () => {
    renderPanel(makeOrder({ deliverySlotStatus: "REQUESTED" }));
    expect(screen.getByRole("button", { name: /Confirm Slot/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Mark Unavailable/i })).toBeInTheDocument();
  });

  it("hides Confirm but still allows Mark Unavailable once a slot is CONFIRMED", () => {
    renderPanel(makeOrder({ deliverySlotStatus: "CONFIRMED" }));
    expect(screen.queryByRole("button", { name: /Confirm Slot/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Mark Unavailable/i })).toBeInTheDocument();
    expect(screen.getByText(/Confirmed/i)).toBeInTheDocument();
  });

  it("hides all actions once a slot is UNAVAILABLE", () => {
    renderPanel(makeOrder({ deliverySlotStatus: "UNAVAILABLE" }));
    expect(screen.queryByRole("button", { name: /Confirm Slot/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Mark Unavailable/i })).not.toBeInTheDocument();
  });

  it("confirms the slot and calls onRefresh", async () => {
    mockConfirm.mockResolvedValue({ data: makeOrder({ deliverySlotStatus: "CONFIRMED" }) } as never);
    const { onRefresh } = renderPanel(makeOrder({ deliverySlotStatus: "REQUESTED" }));

    await userEvent.click(screen.getByRole("button", { name: /Confirm Slot/i }));

    await waitFor(() => expect(mockConfirm).toHaveBeenCalledWith("company-1", "order-1"));
    await waitFor(() => expect(onRefresh).toHaveBeenCalled());
  });

  it("marks the slot unavailable with a reason", async () => {
    mockUnavailable.mockResolvedValue({ data: makeOrder({ deliverySlotStatus: "UNAVAILABLE" }) } as never);
    const { onRefresh } = renderPanel(makeOrder({ deliverySlotStatus: "REQUESTED" }));

    await userEvent.click(screen.getByRole("button", { name: /Mark Unavailable/i }));
    await userEvent.type(screen.getByPlaceholderText(/Reason/i), "Outside zone");
    await userEvent.click(screen.getByRole("button", { name: /^Confirm$/i }));

    await waitFor(() =>
      expect(mockUnavailable).toHaveBeenCalledWith("company-1", "order-1", { reason: "Outside zone" })
    );
    await waitFor(() => expect(onRefresh).toHaveBeenCalled());
  });
});
