import api from "../api";
import type { CompareProduct, CompareBundle } from "../types/comparison";
import type { PagedResponse } from "./vendor";

export const compareApi = {
  compareProducts: (companyId: number, ids: number[]) =>
    api.get<CompareProduct[]>(`/companies/${companyId}/products/compare`, {
      params: { ids: ids.join(",") },
    }),

  compareBundles: (companyId: number, ids: number[]) =>
    api.get<CompareBundle[]>(`/companies/${companyId}/bundles/compare`, {
      params: { ids: ids.join(",") },
    }),

  searchProducts: (companyId: number, q: string) =>
    api.get<PagedResponse<CompareProduct>>(`/companies/${companyId}/products`, {
      params: { q, size: 6, status: "ACTIVE", listed: true },
    }),

  listBundles: (companyId: number) =>
    api.get<PagedResponse<CompareBundle>>(`/companies/${companyId}/bundles`, {
      params: { size: 50 },
    }),
};
