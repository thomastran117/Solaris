export type SavedListType = "WISHLIST" | "GIFT" | "SHOPPING" | "PROJECT";

export interface SavedListItem {
  id: string;
  productId: string;
  productName: string;
  productThumbnailUrl: string | null;
  variantId: string | null;
  variantSku: string | null;
  quantity: number;
  note: string | null;
  purchased: boolean;
  purchasedAt: string | null;
  addedAt: string;
}

export interface SavedList {
  id: string;
  userId: string;
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
  id: string;
  userId: string;
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
  id: string;
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
  productId: string;
  variantId?: string;
  quantity?: number;
  note?: string;
}

export interface UpdateSavedListItemRequest {
  quantity?: number;
  note?: string;
  purchased?: boolean;
}
