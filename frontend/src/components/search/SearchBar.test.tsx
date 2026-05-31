import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { configureStore } from "@reduxjs/toolkit";
import SearchBar from "./SearchBar";
import type { SearchSuggestionsResponse } from "../../types/search";

// ─── mocks ───────────────────────────────────────────────────────────────────

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../../api/catalog", () => ({
  catalogApi: { getSuggestions: vi.fn() },
}));

import { catalogApi } from "../../api/catalog";
const mockGetSuggestions = vi.mocked(catalogApi.getSuggestions);

// ─── helpers ─────────────────────────────────────────────────────────────────

function makeStore() {
  return configureStore({
    reducer: {
      marketplace: () => ({ currentMarketplace: { id: "marketplace-123" } }),
      auth: () => ({}),
      vendor: () => ({}),
      loyalty: () => ({}),
    },
  });
}

function makeSuggestions(overrides: Partial<SearchSuggestionsResponse> = {}): SearchSuggestionsResponse {
  return { products: [], categories: [], brands: [], ...overrides };
}

function renderSearchBar(props: Partial<Parameters<typeof SearchBar>[0]> = {}) {
  const onSearch = vi.fn();
  render(
    <Provider store={makeStore()}>
      <MemoryRouter>
        <SearchBar onSearch={onSearch} placeholder="Search products" {...props} />
      </MemoryRouter>
    </Provider>
  );
  return { onSearch };
}

// Helper: create a suggestionsFetcher that resolves to the given data.
// Using this prop isolates each test from stale debounce timers fired by previous tests,
// since those timers hold a closure over the old render's fetcher.
function makeFetcher(data: SearchSuggestionsResponse) {
  return vi.fn().mockResolvedValue(data);
}

// ─── setup ───────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
});

// ─── tests ───────────────────────────────────────────────────────────────────

describe("SearchBar", () => {
  it("renders search input with correct placeholder", () => {
    renderSearchBar({ placeholder: "Find something" });
    expect(screen.getByPlaceholderText("Find something")).toBeInTheDocument();
  });

  it("does not fetch suggestions for a single character", async () => {
    const fetcher = makeFetcher(makeSuggestions());
    renderSearchBar({ suggestionsFetcher: fetcher });

    await userEvent.type(screen.getByRole("textbox"), "a");
    // Wait well past the 250ms debounce to confirm no call was made
    await new Promise((r) => setTimeout(r, 350));

    expect(fetcher).not.toHaveBeenCalled();
  });

  it("shows 3 product tiles after typing a query", async () => {
    const suggestions = makeSuggestions({
      products: [
        { id: "p1", name: "Widget Pro", price: 29.99, discountedPrice: null, thumbnailUrl: "https://cdn/w.jpg" },
        { id: "p2", name: "Widget Lite", price: 9.99, discountedPrice: 7.99, thumbnailUrl: null },
        { id: "p3", name: "Widget Max", price: 49.99, discountedPrice: null, thumbnailUrl: null },
      ],
    });
    renderSearchBar({ suggestionsFetcher: makeFetcher(suggestions) });

    await userEvent.type(screen.getByRole("textbox"), "wi");

    await waitFor(() => expect(screen.getByText("Widget Pro")).toBeInTheDocument());
    expect(screen.getByText("Widget Lite")).toBeInTheDocument();
    expect(screen.getByText("Widget Max")).toBeInTheDocument();
    // Original price shown
    expect(screen.getByText("$29.99")).toBeInTheDocument();
    // Discounted price shown for Widget Lite
    expect(screen.getByText("$7.99")).toBeInTheDocument();
  });

  it("clicking a product tile navigates to /products/:id", async () => {
    const suggestions = makeSuggestions({
      products: [{ id: "prod-abc", name: "Gadget", price: 19.99, discountedPrice: null, thumbnailUrl: null }],
    });
    renderSearchBar({ suggestionsFetcher: makeFetcher(suggestions) });

    await userEvent.type(screen.getByRole("textbox"), "ga");
    await waitFor(() => screen.getByText("Gadget"));

    fireEvent.mouseDown(screen.getByText("Gadget"));

    expect(mockNavigate).toHaveBeenCalledWith("/products/prod-abc");
  });

  it("submitting the form calls onSearch, not navigate", async () => {
    const { onSearch } = renderSearchBar({ suggestionsFetcher: makeFetcher(makeSuggestions()) });

    await userEvent.type(screen.getByRole("textbox"), "headphones");
    fireEvent.submit(screen.getByRole("textbox").closest("form")!);

    expect(onSearch).toHaveBeenCalledWith("headphones");
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it("clicking a category suggestion navigates to /browse?category=", async () => {
    const suggestions = makeSuggestions({ categories: ["Electronics"] });
    renderSearchBar({ suggestionsFetcher: makeFetcher(suggestions) });

    await userEvent.type(screen.getByRole("textbox"), "el");
    await waitFor(() => screen.getByText("Electronics"));

    fireEvent.mouseDown(screen.getByText("Electronics"));

    expect(mockNavigate).toHaveBeenCalledWith("/browse?category=Electronics");
  });

  it("closes the dropdown on outside click", async () => {
    const suggestions = makeSuggestions({
      products: [{ id: "p1", name: "Test Product", price: 10, discountedPrice: null, thumbnailUrl: null }],
    });
    renderSearchBar({ suggestionsFetcher: makeFetcher(suggestions) });

    await userEvent.type(screen.getByRole("textbox"), "te");
    await waitFor(() => screen.getByText("Test Product"));

    fireEvent.mouseDown(document.body);

    await waitFor(() => {
      expect(screen.queryByText("Test Product")).not.toBeInTheDocument();
    });
  });

  it("uses catalogApi.getSuggestions when no suggestionsFetcher prop is given", async () => {
    mockGetSuggestions.mockResolvedValue({
      data: makeSuggestions({ products: [{ id: "x", name: "API Product", price: 5, discountedPrice: null, thumbnailUrl: null }] }),
    } as never);

    renderSearchBar(); // no suggestionsFetcher → falls back to catalogApi

    await userEvent.type(screen.getByRole("textbox"), "ap");
    await waitFor(() => expect(screen.getByText("API Product")).toBeInTheDocument());

    expect(mockGetSuggestions).toHaveBeenCalled();
  });
});
