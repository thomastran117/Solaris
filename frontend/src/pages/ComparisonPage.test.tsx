import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { configureStore } from "@reduxjs/toolkit";
import ComparisonPage from "./ComparisonPage";
import type { ProductComparisonResponse, CompareBundle } from "../types/comparison";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../api/compare", () => ({
  compareApi: {
    compareProducts: vi.fn(),
    compareBundles: vi.fn(),
    listBundles: vi.fn(),
  },
}));

vi.mock("../api/catalog", () => ({
  catalogApi: { companyCatalogSearch: vi.fn() },
}));

import { compareApi } from "../api/compare";
import { catalogApi } from "../api/catalog";

const mockCompareProducts = vi.mocked(compareApi.compareProducts);
const mockCompareBundles = vi.mocked(compareApi.compareBundles);
const mockListBundles = vi.mocked(compareApi.listBundles);
const mockCompanySearch = vi.mocked(catalogApi.companyCatalogSearch);

// ─── helpers ─────────────────────────────────────────────────────────────────

function makeStore(marketplaceId: string | null = "mkt-1") {
  return configureStore({
    reducer: {
      marketplace: () => ({ currentMarketplace: marketplaceId ? { id: marketplaceId } : null }),
      auth: () => ({ accessToken: "token" }),
    },
  });
}

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderPage(initialPath: string, marketplaceId: string | null = "mkt-1") {
  render(
    <Provider store={makeStore(marketplaceId)}>
      <QueryClientProvider client={makeClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/c/:id/compare" element={<ComparisonPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  );
}

function makeMatrix(): ProductComparisonResponse {
  return {
    products: [
      { productId: "p1", name: "Alpha", price: 10, currency: "USD", rating: 4.5, reviewCount: 3, stockStatus: "IN_STOCK", imageUrl: null },
      { productId: "p2", name: "Beta", price: 20, currency: "USD", rating: null, reviewCount: 0, stockStatus: "LOW_STOCK", imageUrl: null },
    ],
    attributes: [{ attributeName: "Material", valuesByProductId: { p1: "Steel", p2: "Wood" } }],
  };
}

function makeBundle(id: string, name: string): CompareBundle {
  return {
    id,
    companyId: "company-1",
    name,
    description: null,
    thumbnailUrl: null,
    price: 49.99,
    compareAtPrice: null,
    currency: "USD",
    status: "ACTIVE",
    listed: true,
    items: [],
    createdAt: "",
    updatedAt: "",
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockCompanySearch.mockResolvedValue({ data: { items: [] } } as never);
  mockListBundles.mockResolvedValue({ data: { items: [] } } as never);
});

// ─── tests ───────────────────────────────────────────────────────────────────

describe("ComparisonPage", () => {
  it("renders the product comparison matrix on success", async () => {
    mockCompareProducts.mockResolvedValue({ data: makeMatrix() } as never);

    renderPage("/c/company-1/compare?ids=p1,p2");

    await waitFor(() => expect(screen.getByText("Alpha")).toBeInTheDocument());
    expect(screen.getByText("Beta")).toBeInTheDocument();
    expect(screen.getByText("Material")).toBeInTheDocument();
    expect(mockCompareProducts).toHaveBeenCalledWith("mkt-1", ["p1", "p2"]);
  });

  it("shows the empty state when no items are selected", () => {
    renderPage("/c/company-1/compare");
    expect(screen.getByText("Nothing to compare yet")).toBeInTheDocument();
    expect(mockCompareProducts).not.toHaveBeenCalled();
  });

  it("prompts to browse the marketplace when no marketplace context is set", () => {
    renderPage("/c/company-1/compare?ids=p1,p2", null);
    expect(
      screen.getByText("Browse the marketplace to compare products side by side.")
    ).toBeInTheDocument();
    expect(mockCompareProducts).not.toHaveBeenCalled();
  });

  it("renders an error state when the request fails", async () => {
    mockCompareProducts.mockRejectedValue(new Error("boom"));

    renderPage("/c/company-1/compare?ids=p1,p2");

    await waitFor(() =>
      expect(
        screen.getByText("Failed to load comparison data. Please try again.")
      ).toBeInTheDocument()
    );
  });

  it("uses the company-scoped bundle endpoint in bundle mode", async () => {
    mockCompareBundles.mockResolvedValue({
      data: [makeBundle("b1", "Starter Kit"), makeBundle("b2", "Pro Kit")],
    } as never);

    renderPage("/c/company-1/compare?type=bundle&ids=b1,b2");

    await waitFor(() => expect(screen.getByText("Starter Kit")).toBeInTheDocument());
    expect(mockCompareBundles).toHaveBeenCalledWith("company-1", ["b1", "b2"]);
    expect(mockCompareProducts).not.toHaveBeenCalled();
  });
});
