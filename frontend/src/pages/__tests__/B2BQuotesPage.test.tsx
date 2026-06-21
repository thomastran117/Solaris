import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { configureStore } from "@reduxjs/toolkit";
import B2BQuotesPage from "../B2BQuotesPage";
import type { Quote, PagedResponse } from "../../types/b2b";

vi.mock("../../api/b2b", () => ({
  listMyQuotes: vi.fn(),
  requestQuote: vi.fn(),
  acceptQuote: vi.fn(),
  rejectQuote: vi.fn(),
}));

import { listMyQuotes, acceptQuote } from "../../api/b2b";

const mockList = vi.mocked(listMyQuotes);
const mockAccept = vi.mocked(acceptQuote);

function makeStore() {
  return configureStore({
    reducer: { auth: () => ({ companyId: "c-1", accessToken: "token" }) },
  });
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <Provider store={makeStore()}>
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <B2BQuotesPage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  );
}

function stubQuote(overrides: Partial<Quote> = {}): Quote {
  return {
    id: "q-1",
    vendorCompanyId: "c-1",
    buyerUserId: "u-1",
    status: "PENDING_BUYER",
    expiresAt: "2999-01-01T00:00:00Z",
    buyerMessage: null,
    vendorNote: "Best price",
    paymentTerms: "NET_30",
    currency: "USD",
    convertedOrderId: null,
    totalCents: 12000,
    items: [
      {
        id: "i-1",
        productId: "p-1",
        variantId: null,
        productName: "Widget",
        quantity: 2,
        unitPriceCents: 6000,
        totalPriceCents: 12000,
      },
    ],
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-01T00:00:00Z",
    ...overrides,
  };
}

function paged(items: Quote[]): PagedResponse<Quote> {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("B2BQuotesPage", () => {
  it("renders quotes with accept/reject for PENDING_BUYER", async () => {
    mockList.mockResolvedValue(paged([stubQuote()]));
    renderPage();

    expect(await screen.findByRole("button", { name: "Accept" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
    expect(screen.getByText(/Best price/)).toBeInTheDocument();
  });

  it("calls acceptQuote when Accept is clicked", async () => {
    mockList.mockResolvedValue(paged([stubQuote()]));
    mockAccept.mockResolvedValue({ id: "order-1" } as never);
    renderPage();

    const acceptBtn = await screen.findByRole("button", { name: "Accept" });
    await userEvent.click(acceptBtn);

    await waitFor(() => expect(mockAccept).toHaveBeenCalledWith("q-1"));
  });

  it("does not show accept/reject for converted quotes", async () => {
    mockList.mockResolvedValue(paged([stubQuote({ status: "CONVERTED" })]));
    renderPage();

    await screen.findByText(/Best price/);
    expect(screen.queryByRole("button", { name: "Accept" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
  });
});
