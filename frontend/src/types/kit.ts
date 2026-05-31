export type KitStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "ARCHIVED";

export interface KitSlotChoice {
  id: string;
  productId: string;
  productName: string;
  productThumbnailUrl: string | null;
  variantId: string | null;
  variantTitle: string | null;
  variantSku: string | null;
  basePrice: number;
  priceDelta: number | null;
  effectivePrice: number;
  defaultChoice: boolean;
  displayOrder: number;
  inStock: boolean;
}

export interface KitSlot {
  id: string;
  name: string;
  description: string | null;
  required: boolean;
  minQty: number;
  maxQty: number;
  displayOrder: number;
  choices: KitSlotChoice[];
}

export interface ProductKit {
  id: string;
  companyId: string;
  name: string;
  description: string | null;
  thumbnailUrl: string | null;
  computedMinPrice: number;
  computedMaxPrice: number;
  compareAtPrice: number | null;
  currency: string;
  status: KitStatus;
  listed: boolean;
  purchasable: boolean;
  slots: KitSlot[];
  createdAt: string;
  updatedAt: string;
}

export type SlotSelectionState =
  | { status: "empty" }
  | { status: "selected"; choiceId: string; quantity: number };
