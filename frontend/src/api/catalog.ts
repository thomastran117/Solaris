import api from "../api";
import type { CatalogSearchResponse, SearchSuggestionsResponse } from "../types/search";

export interface CatalogProduct {
  id: number;
  companyId: number;
  marketplaceId: number;
  vendorId: number | null;
  vendorName: string | null;
  vendorTier: string | null;
  name: string;
  description: string | null;
  sku: string | null;
  price: number;
  compareAtPrice: number | null;
  currency: string;
  category: string | null;
  brand: string | null;
  tags: string | null;
  thumbnailUrl: string | null;
  images: { id: number; imageUrl: string; displayOrder: number }[];
  variants: { id: number; sku: string | null; price: number; stock: number | null; option1: string | null; option2: string | null; option3: string | null }[];
  stock: number | null;
  status: string;
  featured: boolean;
  purchasable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface VendorStorefront {
  vendorId: number;
  marketplaceId: number;
  vendorCompanyName: string;
  vendorDescription: string | null;
  vendorLogoUrl: string | null;
  vendorTier: string;
  vendorStatus: string;
  featuredProducts: CatalogProduct[];
  totalListedProducts: number;
}

export interface CatalogPagedResponse {
  items: CatalogProduct[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface CatalogSearchParams {
  q?: string;
  category?: string;
  brand?: string;
  minPrice?: number;
  maxPrice?: number;
  featured?: boolean;
  vendorId?: number;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "asc" | "desc";
}

export const catalogApi = {
  search: (marketplaceId: number, params: CatalogSearchParams = {}) =>
    api.get<CatalogSearchResponse>(`/marketplaces/${marketplaceId}/catalog/products`, { params }),

  getSuggestions: (marketplaceId: number, q: string, limit = 8) =>
    api.get<SearchSuggestionsResponse>(
      `/marketplaces/${marketplaceId}/catalog/search/suggestions`,
      { params: { q, limit } }
    ),

  getProduct: (marketplaceId: number, productId: number) =>
    api.get<CatalogProduct>(`/marketplaces/${marketplaceId}/catalog/products/${productId}`),

  getVendorStorefront: (marketplaceId: number, vendorId: number) =>
    api.get<VendorStorefront>(`/marketplaces/${marketplaceId}/catalog/vendors/${vendorId}/storefront`),
};
