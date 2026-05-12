import api from "../api";
import type { AvailabilityEstimate } from "../types/availability";
import type { CatalogSearchResponse, SearchSuggestionsResponse } from "../types/search";

/**
 * Type matches backend ProductRuleType. UI may key off the value to swap iconography
 * but should treat unknown values defensively.
 */
export type PromotionRuleType =
  | "PERCENTAGE_OFF"
  | "FIXED_OFF"
  | "BOGO"
  | "TIERED_PRICE"
  | "FREE_SHIPPING";

/**
 * Lightweight in-window promotion summary attached to product responses.
 * Order/cart pricing is authoritative and runs server-side — this is for badge/strikethrough UI only.
 */
export interface ActivePromotionSummary {
  id: number;
  ruleType: PromotionRuleType;
  percentOff: number | null;
  amountOff: number | null;
  endDate: string | null;
}

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
  scheduledPublishAt: string | null;
  publishedAt: string | null;
  featured: boolean;
  purchasable: boolean;
  createdAt: string;
  updatedAt: string;
  activePromotion: ActivePromotionSummary | null;
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

export type AdminProductStatus = "DRAFT" | "ACTIVE" | "SCHEDULED" | "ARCHIVED";

/**
 * Admin/vendor view of a product. Shape mirrors backend ProductResponse, which is a
 * superset of the customer-facing CatalogProduct. Admin pages can edit fields that the
 * customer-facing endpoints don't surface (e.g. {@link AdminProduct.status}).
 */
export interface AdminProduct {
  id: number;
  companyId: number;
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
  stock: number | null;
  status: AdminProductStatus;
  scheduledPublishAt: string | null;
  publishedAt: string | null;
  featured: boolean;
  purchasable: boolean;
  listed: boolean;
  boostWeight: number | null;
  pinnedUntil: string | null;
  pinnedRank: number | null;
  createdAt: string;
  updatedAt: string;
  activePromotion: ActivePromotionSummary | null;
}

export interface AdminProductsPage {
  items: AdminProduct[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface AdminProductWritePayload {
  name: string;
  description?: string | null;
  sku?: string | null;
  price: number;
  compareAtPrice?: number | null;
  currency?: string;
  category?: string | null;
  brand?: string | null;
  tags?: string | null;
  thumbnailUrl?: string | null;
  stock?: number | null;
  status?: AdminProductStatus;
  /** ISO 8601 instant. Required when status is SCHEDULED. */
  scheduledPublishAt?: string | null;
  featured?: boolean;
  purchasable?: boolean;
  listed?: boolean;
  /** Merchandising: pin/boost. Sent as null to clear. */
  boostWeight?: number | null;
  pinnedUntil?: string | null;
  pinnedRank?: number | null;
}

/**
 * Body for {@code PATCH /companies/{cId}/products/{pId}/merchandising}.
 * All fields are nullable — sending null clears the value.
 */
export interface UpdateProductMerchandisingPayload {
  boostWeight: number | null;
  pinnedUntil: string | null;
  pinnedRank: number | null;
}

export interface AdminListParams {
  q?: string;
  status?: AdminProductStatus;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "asc" | "desc";
}

export const adminProductsApi = {
  list: (companyId: number, params: AdminListParams = {}) =>
    api.get<AdminProductsPage>(`/companies/${companyId}/products`, { params }),

  get: (companyId: number, productId: number) =>
    api.get<AdminProduct>(`/companies/${companyId}/products/${productId}`),

  create: (companyId: number, payload: AdminProductWritePayload) =>
    api.post<AdminProduct>(`/companies/${companyId}/products`, payload),

  update: (companyId: number, productId: number, payload: AdminProductWritePayload) =>
    api.patch<AdminProduct>(`/companies/${companyId}/products/${productId}`, payload),

  remove: (companyId: number, productId: number) =>
    api.delete<void>(`/companies/${companyId}/products/${productId}`),
};

export const adminMerchandisingApi = {
  /** Update a product's pin/boost without touching the rest of the catalog fields. */
  update: (companyId: number, productId: number, payload: UpdateProductMerchandisingPayload) =>
    api.patch<AdminProduct>(`/companies/${companyId}/products/${productId}/merchandising`, payload),
};

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

  getAvailability: (
    marketplaceId: number,
    productId: number,
    opts: { variantId?: number; lat?: number; lng?: number } = {}
  ) =>
    api.get<AvailabilityEstimate>(
      `/marketplaces/${marketplaceId}/catalog/products/${productId}/availability`,
      { params: opts }
    ),
};
