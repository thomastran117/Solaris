import { describe, it, expect } from "vitest";
import {
  CATALOG_SORT_OPTIONS,
  DEFAULT_CATALOG_SORT,
  parseCatalogSort,
  selectedCatalogSortIndex,
} from "./catalogSort";

describe("catalogSort", () => {
  it("defaults to relevance for missing or unknown values", () => {
    expect(DEFAULT_CATALOG_SORT).toBe("relevance");
    expect(parseCatalogSort(null)).toBe("relevance");
    expect(parseCatalogSort("")).toBe("relevance");
    expect(parseCatalogSort("created_at")).toBe("relevance");
  });

  it("keeps known sort fields", () => {
    expect(parseCatalogSort("createdAt")).toBe("createdAt");
    expect(parseCatalogSort("price")).toBe("price");
    expect(parseCatalogSort("stock")).toBe("stock");
  });

  it("gives Relevance and Newest distinct sort values", () => {
    const relevance = CATALOG_SORT_OPTIONS.find((o) => o.label === "Relevance");
    const newest = CATALOG_SORT_OPTIONS.find((o) => o.label === "Newest");
    expect(relevance?.sort).toBe("relevance");
    expect(newest?.sort).toBe("createdAt");
  });

  it("resolves the selected option index, defaulting to Relevance", () => {
    expect(selectedCatalogSortIndex("createdAt", "desc")).toBe(
      CATALOG_SORT_OPTIONS.findIndex((o) => o.label === "Newest")
    );
    expect(selectedCatalogSortIndex("price", "asc")).toBe(1);
    expect(selectedCatalogSortIndex("stock", "asc")).toBe(0);
  });
});
