import { parseCatalogSort } from "../components/search/catalogSort";
import type { CatalogSearchParams } from "../api/catalog";
import type { CatalogSortField } from "../types/search";

/** Page size for marketplace browse and company storefront results. */
export const CATALOG_PAGE_SIZE = 24;

/**
 * Catalog filters as they appear in the URL (`/browse`, `/c/:id`). Raw strings are
 * kept for price bounds because they are part of the TanStack Query key; parsing
 * them to numbers here would change the key shape and stop server-prefetched data
 * from matching the client query.
 */
export interface CatalogUrlParams {
  q: string;
  category: string | null;
  brand: string | null;
  minPrice: string | null;
  maxPrice: string | null;
  sort: CatalogSortField;
  direction: "asc" | "desc";
  page: number;
}

type ParamSource =
  | { get(name: string): string | null }
  | Record<string, string | string[] | undefined>;

function reader(source: ParamSource): (name: string) => string | null {
  if (typeof (source as { get?: unknown }).get === "function") {
    return (name) => (source as { get(n: string): string | null }).get(name);
  }
  const record = source as Record<string, string | string[] | undefined>;
  return (name) => {
    const v = record[name];
    return Array.isArray(v) ? (v[0] ?? null) : (v ?? null);
  };
}

/**
 * Parses catalog filters from `useSearchParams()` in the browser or from a page's
 * awaited `searchParams` prop on the server, with identical results for both.
 */
export function parseCatalogUrlParams(source: ParamSource): CatalogUrlParams {
  const get = reader(source);
  const rawPage = Number(get("page") ?? "0");
  return {
    q: get("q") ?? "",
    category: get("category"),
    brand: get("brand"),
    minPrice: get("minPrice"),
    maxPrice: get("maxPrice"),
    sort: parseCatalogSort(get("sort")),
    direction: get("direction") === "asc" ? "asc" : "desc",
    page: Number.isFinite(rawPage) && rawPage >= 0 ? Math.floor(rawPage) : 0,
  };
}

/** The catalog search request for the given URL filters. */
export function catalogSearchRequest(p: CatalogUrlParams): CatalogSearchParams {
  return {
    q: p.q || undefined,
    category: p.category ?? undefined,
    brand: p.brand ?? undefined,
    minPrice: p.minPrice ? Number(p.minPrice) : undefined,
    maxPrice: p.maxPrice ? Number(p.maxPrice) : undefined,
    sort: p.sort,
    direction: p.direction,
    page: p.page,
    size: CATALOG_PAGE_SIZE,
  };
}

/** Ordered key segments for the filters (shared by browse and company catalog keys). */
export function catalogKeyParts(p: CatalogUrlParams) {
  return [
    p.q,
    p.category,
    p.brand,
    p.minPrice,
    p.maxPrice,
    p.sort,
    p.direction,
    p.page,
  ] as const;
}
