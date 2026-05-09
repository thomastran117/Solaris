export type SavedListType = "WISHLIST" | "GIFT" | "SHOPPING" | "PROJECT";

export interface SavedListItem {
  id: number;
  productId: number;
  productName: string;
  productThumbnailUrl: string | null;
  variantId: number | null;
  variantSku: string | null;
  quantity: number;
  note: string | null;
  purchased: boolean;
  purchasedAt: string | null;
  addedAt: string;
}

export interface SavedList {
  id: number;
  userId: number;
  name: string;
  type: SavedListType;
  description: string | null;
  isPublic: boolean;
  shareSlug: string | null;
  items: SavedListItem[];
  createdAt: string;
  updatedAt: string;
}

export interface SavedListSummary {
  id: number;
  userId: number;
  name: string;
  type: SavedListType;
  description: string | null;
  isPublic: boolean;
  shareSlug: string | null;
  itemCount: number;
  purchasedCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PublicSavedList {
  id: number;
  ownerDisplayName: string;
  name: string;
  type: SavedListType;
  description: string | null;
  shareSlug: string;
  items: SavedListItem[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateSavedListRequest {
  name: string;
  type: SavedListType;
  description?: string;
  isPublic?: boolean;
}

export interface UpdateSavedListRequest {
  name?: string;
  type?: SavedListType;
  description?: string;
  isPublic?: boolean;
}

export interface AddSavedListItemRequest {
  productId: number;
  variantId?: number;
  quantity?: number;
  note?: string;
}

export interface UpdateSavedListItemRequest {
  quantity?: number;
  note?: string;
  purchased?: boolean;
}
