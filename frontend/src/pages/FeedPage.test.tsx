import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { configureStore } from "@reduxjs/toolkit";
import FeedPage from "./FeedPage";
import type { CatalogPagedResponse } from "../api/catalog";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../api/catalog", () => ({
  catalogApi: { getFeed: vi.fn() },
}));

import { catalogApi } from "../api/catalog";
const mockGetFeed = vi.mocked(catalogApi.getFeed);

// ─── helpers ─────────────────────────────────────────────────────────────────

function makeStore(marketplaceId: string | null = "marketplace-1") {
  return configureStore({
    reducer: {
      marketplace: () => ({
        currentMarketplace: marketplaceId ? { id: marketplaceId } : null,
      }),
      auth: () => ({ accessToken: "token" }),
      vendor: () => ({}),
      loyalty: () => ({}),
    },
  });
}

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function renderFeedPage(marketplaceId: string | null = "marketplace-1") {
  render(
    <Provider store={makeStore(marketplaceId)}>
      <QueryClientProvider client={makeClient()}>
        <MemoryRouter>
          <FeedPage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  );
}

function makePagedResponse(
  items: CatalogPagedResponse["items"] = [],
  overrides: Partial<Omit<CatalogPagedResponse, "items">> = {}
): CatalogPagedResponse {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / 20) || 1,
    hasNext: false,
    hasPrevious: false,
    ...overrides,
  };
}

function makeProduct(id: string): CatalogPagedResponse["items"][number] {
  return {
    id,
    companyId: "company-1",
    marketplaceId: "marketplace-1",
    vendorId: null,
    vendorName: null,
    vendorTier: null,
    name: `Product ${id}`,
    description: null,
    sku: null,
    price: 9.99,
    compareAtPrice: null,
    currency: "USD",
    category: "Books",
    brand: null,
    tags: null,
    thumbnailUrl: null,
    images: [],
    variants: [],
    stock: null,
    status: "ACTIVE",
    scheduledPublishAt: null,
    publishedAt: null,
    featured: false,
    purchasable: true,
    preorderEnabled: false,
    preorderExpectedDate: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    activePromotion: null,
  };
}

// ─── tests ───────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
});

describe("FeedPage", () => {
  it("shows loading skeleton initially", () => {
    mockGetFeed.mockReturnValue(new Promise(() => {})); // never resolves

    renderFeedPage();

    const skeletonCards = document.querySelectorAll(".animate-pulse");
    expect(skeletonCards.length).toBe(12);
  });

  it("renders product cards on success", async () => {
    mockGetFeed.mockResolvedValue({
      data: makePagedResponse([makeProduct("p1"), makeProduct("p2"), makeProduct("p3")]),
    } as never);

    renderFeedPage();

    await waitFor(() => {
      expect(screen.getByText("Product p1")).toBeInTheDocument();
      expect(screen.getByText("Product p2")).toBeInTheDocument();
      expect(screen.getByText("Product p3")).toBeInTheDocument();
    });
  });

  it("shows empty state when feed has no items", async () => {
    mockGetFeed.mockResolvedValue({
      data: makePagedResponse([]),
    } as never);

    renderFeedPage();

    await waitFor(() => {
      expect(screen.getByText("Your feed is empty")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /browse products/i })).toBeInTheDocument();
    });
  });

  it("shows pagination when totalPages > 1", async () => {
    mockGetFeed.mockResolvedValue({
      data: makePagedResponse(
        Array.from({ length: 20 }, (_, i) => makeProduct(`p${i}`)),
        { totalPages: 3, totalElements: 60, hasNext: true }
      ),
    } as never);

    renderFeedPage();

    await waitFor(() => {
      const nextButtons = screen.getAllByRole("button").filter(
        (b) => !b.hasAttribute("disabled") && b.getAttribute("class")?.includes("rounded-full")
      );
      expect(nextButtons.length).toBeGreaterThan(0);
    });
  });

  it("does not call getFeed without marketplaceId", () => {
    renderFeedPage(null);

    expect(mockGetFeed).not.toHaveBeenCalled();
  });
});
