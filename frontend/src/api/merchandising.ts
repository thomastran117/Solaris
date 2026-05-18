import api from "../api";
import type {
  Collection,
  CollectionProductRow,
  CollectionStatus,
  CollectionType,
} from "../types/collection";

export interface CollectionWritePayload {
  name: string;
  slug: string;
  description?: string | null;
  imageUrl?: string | null;
  type: CollectionType;
  status?: CollectionStatus;
  featured?: boolean;
  featuredRank?: number | null;
  /** JSON body. Required when type is DYNAMIC. */
  rulesJson?: string | null;
}

export interface AddCollectionProductPayload {
  productId: string;
  boostWeight?: number | null;
  pinnedRank?: number | null;
}

export interface UpdateCollectionProductPayload {
  boostWeight: number | null;
  pinnedRank: number | null;
}

export interface CollectionListParams {
  type?: CollectionType;
  status?: CollectionStatus;
  featured?: boolean;
  page?: number;
  size?: number;
}

export interface CollectionsPage {
  items: Collection[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface CollectionProductsPage {
  items: CollectionProductRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const adminCollectionsApi = {
  list: (companyId: string, params: CollectionListParams = {}) =>
    api.get<CollectionsPage>(`/companies/${companyId}/collections`, { params }),

  get: (companyId: string, collectionId: string) =>
    api.get<Collection>(`/companies/${companyId}/collections/${collectionId}`),

  create: (companyId: string, payload: CollectionWritePayload) =>
    api.post<Collection>(`/companies/${companyId}/collections`, payload),

  update: (companyId: string, collectionId: string, payload: Partial<CollectionWritePayload>) =>
    api.patch<Collection>(`/companies/${companyId}/collections/${collectionId}`, payload),

  remove: (companyId: string, collectionId: string) =>
    api.delete<void>(`/companies/${companyId}/collections/${collectionId}`),

  refresh: (companyId: string, collectionId: string) =>
    api.post<Collection>(`/companies/${companyId}/collections/${collectionId}/refresh`),

  // --- Membership ---

  listProducts: (companyId: string, collectionId: string, page = 0, size = 20) =>
    api.get<CollectionProductsPage>(
      `/companies/${companyId}/collections/${collectionId}/products`,
      { params: { page, size } }
    ),

  addProduct: (companyId: string, collectionId: string, payload: AddCollectionProductPayload) =>
    api.post<CollectionProductRow>(
      `/companies/${companyId}/collections/${collectionId}/products`,
      payload
    ),

  updateProduct: (
    companyId: string,
    collectionId: string,
    productId: string,
    payload: UpdateCollectionProductPayload
  ) =>
    api.patch<CollectionProductRow>(
      `/companies/${companyId}/collections/${collectionId}/products/${productId}`,
      payload
    ),

  removeProduct: (companyId: string, collectionId: string, productId: string) =>
    api.delete<void>(
      `/companies/${companyId}/collections/${collectionId}/products/${productId}`
    ),
};

export const marketplaceCollectionsApi = {
  listFeatured: (marketplaceId: string) =>
    api.get<Collection[]>(`/marketplaces/${marketplaceId}/collections/featured`),

  listFeaturedForVendor: (marketplaceId: string, vendorId: string) =>
    api.get<Collection[]>(
      `/marketplaces/${marketplaceId}/collections/featured/vendor/${vendorId}`
    ),

  getBySlug: (marketplaceId: string, slug: string) =>
    api.get<Collection>(`/marketplaces/${marketplaceId}/collections/${slug}`),

  listProductsBySlug: (marketplaceId: string, slug: string, page = 0, size = 20) =>
    api.get<CollectionProductsPage>(
      `/marketplaces/${marketplaceId}/collections/${slug}/products`,
      { params: { page, size } }
    ),
};
