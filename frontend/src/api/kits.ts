import api from "../api";
import type { ProductKit, KitStatus } from "../types/kit";

export interface KitSlotChoicePayload {
  productId: string;
  variantId?: string | null;
  priceDelta?: number | null;
  defaultChoice?: boolean;
  displayOrder?: number;
}

export interface KitSlotPayload {
  name: string;
  description?: string | null;
  required?: boolean;
  minQty?: number;
  maxQty?: number;
  displayOrder?: number;
  choices?: KitSlotChoicePayload[];
}

export interface CreateKitPayload {
  name: string;
  description?: string | null;
  thumbnailUrl?: string | null;
  currency?: string;
  compareAtPrice?: number | null;
  status?: KitStatus;
  listed?: boolean;
  slots: KitSlotPayload[];
}

export interface UpdateKitPayload {
  name?: string;
  description?: string | null;
  thumbnailUrl?: string | null;
  currency?: string;
  compareAtPrice?: number | null;
  status?: KitStatus;
  listed?: boolean;
  slots?: KitSlotPayload[];
}

export interface ImportFromCollectionPayload {
  collectionId: string;
  slotId: string;
}

export interface KitsPage {
  items: ProductKit[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const kitsApi = {
  list: (companyId: string, params: { status?: KitStatus; page?: number; size?: number } = {}) =>
    api.get<KitsPage>(`/companies/${companyId}/kits`, { params }),

  get: (companyId: string, kitId: string) =>
    api.get<ProductKit>(`/companies/${companyId}/kits/${kitId}`),

  create: (companyId: string, payload: CreateKitPayload) =>
    api.post<ProductKit>(`/companies/${companyId}/kits`, payload),

  update: (companyId: string, kitId: string, payload: UpdateKitPayload) =>
    api.patch<ProductKit>(`/companies/${companyId}/kits/${kitId}`, payload),

  remove: (companyId: string, kitId: string) =>
    api.delete<void>(`/companies/${companyId}/kits/${kitId}`),

  importFromCollection: (companyId: string, kitId: string, payload: ImportFromCollectionPayload) =>
    api.post<ProductKit>(`/companies/${companyId}/kits/${kitId}/import-collection`, payload),
};
