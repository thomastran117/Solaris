import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { setMockUrl } from "../test/nextNavigation";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { configureStore } from "@reduxjs/toolkit";
import BrowsePage from "./BrowsePage";
import type { CatalogSearchResponse } from "../types/search";

// ─── mocks ───────────────────────────────────────────────────────────────────

vi.mock("../api/catalog", () => ({
  catalogApi: { search: vi.fn(), getSuggestions: vi.fn() },
}));

import { catalogApi } from "../api/catalog";
const mockSearch = vi.mocked(catalogApi.search);

// ─── helpers ─────────────────────────────────────────────────────────────────

function makeStore() {
  return configureStore({
    reducer: {
      marketplace: () => ({ currentMarketplace: { id: "marketplace-1" } }),
      auth: () => ({ accessToken: null }),
      vendor: () => ({}),
      loyalty: () => ({}),
    },
  });
}

function emptyResponse(): CatalogSearchResponse {
  return {
    items: [],
    page: 0,
    size: 24,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
    facets: { categories: [], brands: [], priceRanges: [] },
  };
}

function renderBrowse(initialPath = "/browse") {
  setMockUrl(initialPath);
  render(
    <Provider store={makeStore()}>
      <QueryClientProvider
        client={
          new QueryClient({ defaultOptions: { queries: { retry: false } } })
        }
      >
        <BrowsePage />
      </QueryClientProvider>
    </Provider>
  );
}

function lastSearchParams() {
  const calls = mockSearch.mock.calls;
  return calls[calls.length - 1][1];
}

// ─── tests ───────────────────────────────────────────────────────────────────

describe("BrowsePage sort", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearch.mockResolvedValue({ data: emptyResponse() } as Awaited<
      ReturnType<typeof catalogApi.search>
    >);
  });

  it("requests relevance ordering by default", async () => {
    renderBrowse();

    await waitFor(() => expect(mockSearch).toHaveBeenCalled());
    expect(lastSearchParams()).toMatchObject({
      sort: "relevance",
      direction: "desc",
    });
    expect(screen.getByRole("combobox")).toHaveDisplayValue("Relevance");
  });

  it("sends createdAt for Newest and distinguishes it from Relevance", async () => {
    const user = userEvent.setup();
    renderBrowse();
    await waitFor(() => expect(mockSearch).toHaveBeenCalled());

    await user.selectOptions(screen.getByRole("combobox"), "Newest");

    await waitFor(() =>
      expect(lastSearchParams()).toMatchObject({
        sort: "createdAt",
        direction: "desc",
      })
    );
    expect(screen.getByRole("combobox")).toHaveDisplayValue("Newest");
  });

  it("falls back to relevance for an unrecognised sort in the URL", async () => {
    renderBrowse("/browse?sort=bogus&direction=desc");

    await waitFor(() => expect(mockSearch).toHaveBeenCalled());
    expect(lastSearchParams()).toMatchObject({ sort: "relevance" });
  });
});
