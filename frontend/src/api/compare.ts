import api from "../api";
import type { ProductComparisonResponse, CompareBundle } from "../types/comparison";

interface BundlesPage {
  items: CompareBundle[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const compareApi = {
  /** Server-built attribute matrix for 2–4 marketplace products. */
  compareProducts: (marketplaceId: string, ids: string[]) =>
    api.get<ProductComparisonResponse>(
      `/marketplaces/${marketplaceId}/catalog/products/compare`,
      { params: { ids: ids.join(",") } }
    ),

  /** Full bundle objects for 2–4 bundles in a company storefront (matrix built client-side). */
  compareBundles: (companyId: string, ids: string[]) =>
    api.get<CompareBundle[]>(`/companies/${companyId}/bundles/compare`, {
      params: { ids: ids.join(",") },
    }),

  /**
   * Public storefront bundle listing — drives the bundle search picker (client-side filter).
   * Capped at the backend's max page size (50); stores with more than 50 bundles will only
   * surface the first page in the picker.
   */
  listBundles: (companyId: string) =>
    api.get<BundlesPage>(`/companies/${companyId}/bundles`, {
      params: { size: 50 },
    }),
};
