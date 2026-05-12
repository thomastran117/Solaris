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
  productId: number;
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
  list: (companyId: number, params: CollectionListParams = {}) =>
    api.get<CollectionsPage>(`/companies/${companyId}/collections`, { params }),

  get: (companyId: number, collectionId: number) =>
    api.get<Collection>(`/companies/${companyId}/collections/${collectionId}`),

  create: (companyId: number, payload: CollectionWritePayload) =>
    api.post<Collection>(`/companies/${companyId}/collections`, payload),

  update: (companyId: number, collectionId: number, payload: Partial<CollectionWritePayload>) =>
    api.patch<Collection>(`/companies/${companyId}/collections/${collectionId}`, payload),

  remove: (companyId: number, collectionId: number) =>
    api.delete<void>(`/companies/${companyId}/collections/${collectionId}`),

  refresh: (companyId: number, collectionId: number) =>
    api.post<Collection>(`/companies/${companyId}/collections/${collectionId}/refresh`),

  // --- Membership ---

  listProducts: (companyId: number, collectionId: number, page = 0, size = 20) =>
    api.get<CollectionProductsPage>(
      `/companies/${companyId}/collections/${collectionId}/products`,
      { params: { page, size } }
    ),

  addProduct: (companyId: number, collectionId: number, payload: AddCollectionProductPayload) =>
    api.post<CollectionProductRow>(
      `/companies/${companyId}/collections/${collectionId}/products`,
      payload
    ),

  updateProduct: (
    companyId: number,
    collectionId: number,
    productId: number,
    payload: UpdateCollectionProductPayload
  ) =>
    api.patch<CollectionProductRow>(
      `/companies/${companyId}/collections/${collectionId}/products/${productId}`,
      payload
    ),

  removeProduct: (companyId: number, collectionId: number, productId: number) =>
    api.delete<void>(
      `/companies/${companyId}/collections/${collectionId}/products/${productId}`
    ),
};

export const marketplaceCollectionsApi = {
  listFeatured: (marketplaceId: number) =>
    api.get<Collection[]>(`/marketplaces/${marketplaceId}/collections/featured`),

  listFeaturedForVendor: (marketplaceId: number, vendorId: number) =>
    api.get<Collection[]>(
      `/marketplaces/${marketplaceId}/collections/featured/vendor/${vendorId}`
    ),

  getBySlug: (marketplaceId: number, slug: string) =>
    api.get<Collection>(`/marketplaces/${marketplaceId}/collections/${slug}`),

  listProductsBySlug: (marketplaceId: number, slug: string, page = 0, size = 20) =>
    api.get<CollectionProductsPage>(
      `/marketplaces/${marketplaceId}/collections/${slug}/products`,
      { params: { page, size } }
    ),
};
