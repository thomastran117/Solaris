import { describe, expect, it } from "vitest";
import {
  CATALOG_PAGE_SIZE,
  catalogKeyParts,
  catalogSearchRequest,
  parseCatalogUrlParams,
} from "./catalogParams";

describe("parseCatalogUrlParams", () => {
  it("gives identical results for URLSearchParams (browser) and a searchParams record (server)", () => {
    const qs =
      "q=lamp&category=Home&brand=Acme&minPrice=10&maxPrice=50&sort=price&direction=asc&page=2";
    const fromBrowser = parseCatalogUrlParams(new URLSearchParams(qs));
    const fromServer = parseCatalogUrlParams({
      q: "lamp",
      category: "Home",
      brand: "Acme",
      minPrice: "10",
      maxPrice: "50",
      sort: "price",
      direction: "asc",
      page: "2",
    });
    expect(fromServer).toEqual(fromBrowser);
    expect(fromBrowser).toEqual({
      q: "lamp",
      category: "Home",
      brand: "Acme",
      minPrice: "10",
      maxPrice: "50",
      sort: "price",
      direction: "asc",
      page: 2,
    });
  });

  it("applies defaults when parameters are missing", () => {
    expect(parseCatalogUrlParams(new URLSearchParams())).toEqual({
      q: "",
      category: null,
      brand: null,
      minPrice: null,
      maxPrice: null,
      sort: "relevance",
      direction: "desc",
      page: 0,
    });
  });

  it("normalises invalid sort, direction and page values", () => {
    const p = parseCatalogUrlParams({
      sort: "drop table",
      direction: "sideways",
      page: "-3",
    });
    expect(p.sort).toBe("relevance");
    expect(p.direction).toBe("desc");
    expect(p.page).toBe(0);
    expect(parseCatalogUrlParams({ page: "abc" }).page).toBe(0);
    expect(parseCatalogUrlParams({ page: "1.7" }).page).toBe(1);
  });

  it("takes the first value of a repeated server parameter", () => {
    expect(parseCatalogUrlParams({ q: ["first", "second"] }).q).toBe("first");
  });
});

describe("catalogSearchRequest", () => {
  it("converts URL filters to the backend request", () => {
    const p = parseCatalogUrlParams(
      new URLSearchParams("q=lamp&minPrice=10&page=1")
    );
    expect(catalogSearchRequest(p)).toEqual({
      q: "lamp",
      category: undefined,
      brand: undefined,
      minPrice: 10,
      maxPrice: undefined,
      sort: "relevance",
      direction: "desc",
      page: 1,
      size: CATALOG_PAGE_SIZE,
    });
  });

  it("keeps raw price strings in the key parts", () => {
    const p = parseCatalogUrlParams(new URLSearchParams("minPrice=10"));
    expect(catalogKeyParts(p)).toEqual([
      "",
      null,
      null,
      "10",
      null,
      "relevance",
      "desc",
      0,
    ]);
  });
});
