import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { configureStore } from "@reduxjs/toolkit";
import AdminInventoryTransfersPage from "../AdminInventoryTransfersPage";
import type {
  InventoryTransfer,
  InventoryLocationOption,
  PagedResponse,
  TransferStatus,
} from "../../../types/inventoryTransfers";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../../../api/inventoryTransfers", () => ({
  listTransfers: vi.fn(),
  createTransfer: vi.fn(),
  dispatchTransfer: vi.fn(),
  receiveTransfer: vi.fn(),
  cancelTransfer: vi.fn(),
  listInventoryLocations: vi.fn(),
}));

import {
  listTransfers,
  createTransfer,
  dispatchTransfer,
  listInventoryLocations,
} from "../../../api/inventoryTransfers";

const mockList = vi.mocked(listTransfers);
const mockCreate = vi.mocked(createTransfer);
const mockDispatch = vi.mocked(dispatchTransfer);
const mockLocations = vi.mocked(listInventoryLocations);

// ─── helpers ─────────────────────────────────────────────────────────────────

const COMPANY_ID = "company-123";
const PRODUCT_ID = "11111111-1111-4111-8111-111111111111";
const FROM_ID = "22222222-2222-4222-8222-222222222222";
const TO_ID = "33333333-3333-4333-8333-333333333333";

function makeStore() {
  return configureStore({
    reducer: {
      auth: () => ({ companyId: COMPANY_ID, accessToken: "token" }),
    },
  });
}

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function renderPage() {
  render(
    <Provider store={makeStore()}>
      <QueryClientProvider client={makeClient()}>
        <MemoryRouter>
          <AdminInventoryTransfersPage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  );
}

function stubTransfer(overrides: Partial<InventoryTransfer> = {}): InventoryTransfer {
  return {
    id: "trf-1",
    companyId: COMPANY_ID,
    productId: PRODUCT_ID,
    productName: "Standing Desk",
    fromLocationId: FROM_ID,
    fromLocationName: "Toronto WH",
    toLocationId: TO_ID,
    toLocationName: "Storefront",
    quantity: 5,
    status: "PENDING" as TransferStatus,
    notes: null,
    createdByUserId: "user-1",
    receivedByUserId: null,
    cancelledByUserId: null,
    createdAt: "2026-06-01T00:00:00Z",
    inTransitAt: null,
    receivedAt: null,
    cancelledAt: null,
    ...overrides,
  };
}

function stubPage(transfers: InventoryTransfer[]): PagedResponse<InventoryTransfer> {
  return {
    items: transfers,
    page: 0,
    size: 20,
    totalElements: transfers.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
}

const LOCATIONS: InventoryLocationOption[] = [
  { id: FROM_ID, name: "Toronto WH", code: "TOR" },
  { id: TO_ID, name: "Storefront", code: "STR" },
];

// ─── tests ────────────────────────────────────────────────────────────────────

describe("AdminInventoryTransfersPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLocations.mockResolvedValue(LOCATIONS);
  });

  it("renders transfer list from API", async () => {
    mockList.mockResolvedValue(stubPage([stubTransfer()]));

    renderPage();

    await waitFor(() =>
      expect(screen.getByText("Standing Desk")).toBeInTheDocument()
    );
    // "Pending" appears both as a filter pill and the row status badge.
    expect(screen.getAllByText("Pending").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole("button", { name: /Dispatch/i })).toBeInTheDocument();
    expect(mockList).toHaveBeenCalledWith(COMPANY_ID, undefined, undefined, 0);
  });

  it("shows empty state when no transfers exist", async () => {
    mockList.mockResolvedValue(stubPage([]));

    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/No transfers found/i)).toBeInTheDocument()
    );
  });

  it("blocks submit and shows validation when required fields are missing", async () => {
    mockList.mockResolvedValue(stubPage([]));
    const user = userEvent.setup();

    renderPage();
    await waitFor(() => screen.getByText(/No transfers found/i));

    await user.click(screen.getByRole("button", { name: /New Transfer/i }));
    // Submit empty form — zod should reject and createTransfer must not be called.
    await user.click(screen.getByRole("button", { name: /Create Transfer/i }));

    await waitFor(() =>
      expect(screen.getByText(/Invalid product ID/i)).toBeInTheDocument()
    );
    expect(mockCreate).not.toHaveBeenCalled();
  });

  it("submits a valid transfer and closes the modal", async () => {
    mockList.mockResolvedValue(stubPage([]));
    mockCreate.mockResolvedValue(stubTransfer());
    const user = userEvent.setup();

    renderPage();
    await waitFor(() => screen.getByText(/No transfers found/i));

    await user.click(screen.getByRole("button", { name: /New Transfer/i }));
    const [fromSelect, toSelect] = screen.getAllByRole("combobox");
    // Wait for the location dropdown options to render before selecting them.
    await waitFor(() =>
      expect(within(fromSelect).getByRole("option", { name: /Toronto WH/i })).toBeInTheDocument()
    );

    await user.type(screen.getByPlaceholderText(/Product ID/i), PRODUCT_ID);
    await user.selectOptions(fromSelect, FROM_ID);
    await user.selectOptions(toSelect, TO_ID);
    const qty = screen.getByRole("spinbutton");
    await user.clear(qty);
    await user.type(qty, "5");

    await user.click(screen.getByRole("button", { name: /Create Transfer/i }));

    await waitFor(() =>
      expect(mockCreate).toHaveBeenCalledWith(
        COMPANY_ID,
        expect.objectContaining({
          productId: PRODUCT_ID,
          fromLocationId: FROM_ID,
          toLocationId: TO_ID,
          quantity: 5,
        })
      )
    );
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /Create Transfer/i })).not.toBeInTheDocument()
    );
  });

  it("dispatch button calls dispatchTransfer for a pending transfer", async () => {
    mockList.mockResolvedValue(stubPage([stubTransfer({ status: "PENDING" })]));
    mockDispatch.mockResolvedValue(stubTransfer({ status: "IN_TRANSIT" }));
    const user = userEvent.setup();

    renderPage();
    await waitFor(() => screen.getByText("Standing Desk"));

    await user.click(screen.getByRole("button", { name: /Dispatch/i }));

    await waitFor(() =>
      expect(mockDispatch).toHaveBeenCalledWith(COMPANY_ID, "trf-1")
    );
  });
});
