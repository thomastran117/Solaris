import type { CatalogPagedResponse } from "../api/catalog";

export interface SearchSuggestionsResponse {
  productNames: string[];
  categories: string[];
  brands: string[];
}

export interface FacetBucket {
  value: string;
  count: number;
}

export interface PriceRangeBucket {
  label: string;
  min: number | null;
  max: number | null;
  count: number;
}

export interface SearchFacets {
  categories: FacetBucket[];
  brands: FacetBucket[];
  priceRanges: PriceRangeBucket[];
}

export interface CatalogSearchResponse extends CatalogPagedResponse {
  facets: SearchFacets;
}
