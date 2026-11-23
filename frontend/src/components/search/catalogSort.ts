import type { CatalogSortField } from "../../types/search";

export interface CatalogSortOption {
  label: string;
  sort: CatalogSortField;
  direction: "asc" | "desc";
}

/** Sort choices shared by the marketplace browse page and company storefronts. */
export const CATALOG_SORT_OPTIONS: readonly CatalogSortOption[] = [
  { label: "Relevance", sort: "relevance", direction: "desc" },
  { label: "Price: Low to High", sort: "price", direction: "asc" },
  { label: "Price: High to Low", sort: "price", direction: "desc" },
  { label: "Newest", sort: "createdAt", direction: "desc" },
];

export const DEFAULT_CATALOG_SORT: CatalogSortField = "relevance";

const SORT_FIELDS: readonly CatalogSortField[] = [
  "relevance",
  "createdAt",
  "price",
  "stock",
  "name",
];

/** Narrows a `?sort=` URL value to a known field, defaulting to relevance. */
export function parseCatalogSort(raw: string | null): CatalogSortField {
  return SORT_FIELDS.find((f) => f === raw) ?? DEFAULT_CATALOG_SORT;
}

/** Index of the option matching the current sort, or 0 (Relevance) when none matches. */
export function selectedCatalogSortIndex(
  sort: CatalogSortField,
  direction: "asc" | "desc"
): number {
  const index = CATALOG_SORT_OPTIONS.findIndex(
    (o) => o.sort === sort && o.direction === direction
  );
  return index >= 0 ? index : 0;
}
