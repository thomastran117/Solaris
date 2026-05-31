import { Heart, Gift, ShoppingCart, Hammer, type LucideIcon } from "lucide-react";
import type { SavedListType } from "../../types/savedList";

export interface ListTypeMeta {
  label: string;
  kicker: string;
  Icon: LucideIcon;
}

export const LIST_TYPE_META: Record<SavedListType, ListTypeMeta> = {
  WISHLIST: { label: "Wishlist", kicker: "WISHLIST", Icon: Heart },
  GIFT: { label: "Gift list", kicker: "GIFT LIST", Icon: Gift },
  SHOPPING: { label: "Shopping list", kicker: "SHOPPING LIST", Icon: ShoppingCart },
  PROJECT: { label: "Project", kicker: "PROJECT", Icon: Hammer },
};

export const ALL_TYPES: SavedListType[] = ["WISHLIST", "GIFT", "SHOPPING", "PROJECT"];

/** Whether to surface the "purchased N/M" progress for a given list type. */
export function showsProgress(type: SavedListType): boolean {
  return type === "SHOPPING" || type === "PROJECT";
}
