import type { ActivePromotionSummary } from "../api/catalog";

export type CollectionType = "STATIC" | "DYNAMIC";
export type CollectionStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export type CollectionMembershipSource = "MANUAL" | "AUTO";

/**
 * Mirror of backend {@code CollectionResponse}. {@link rulesJson} is the raw JSON body for
 * dynamic collections; admin pages parse/produce it through the {@code dynamicRulesSchema}.
 */
export interface Collection {
  id: number;
  companyId: number | null;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  type: CollectionType;
  status: CollectionStatus;
  featured: boolean;
  featuredRank: number | null;
  rulesJson: string | null;
  productCount: number;
  lastMaterialisedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CollectionProductRow {
  id: number;
  collectionId: number;
  productId: number;
  productName: string;
  productSku: string | null;
  productThumbnailUrl: string | null;
  productPrice: number;
  productCurrency: string;
  productStatus: string;
  boostWeight: number | null;
  pinnedRank: number | null;
  source: CollectionMembershipSource;
  createdAt: string;
  activePromotion: ActivePromotionSummary | null;
}

/** Schema for the DYNAMIC collection rules body (mirrors the backend parser). */
export interface DynamicCollectionRules {
  tagsAnyOf: string[];
  categoriesAnyOf: string[];
  brandsAnyOf: string[];
}
