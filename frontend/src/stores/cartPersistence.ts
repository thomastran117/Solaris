import { z } from "zod";
import type { CartLine } from "./cartSlice";

export const CART_STORAGE_KEY = "shopwave.cart.v1";

const base = {
  lineId: z.string().min(1),
  name: z.string(),
  thumbnailUrl: z.string().nullable(),
  price: z.number().finite().nonnegative(),
  currency: z.string().length(3),
  quantity: z.number().int().min(1).max(999),
  marketplaceId: z.string().min(1),
  companyId: z.string().min(1),
};

const lineSchema = z.discriminatedUnion("kind", [
  z.object({
    ...base,
    kind: z.literal("product"),
    productId: z.string().min(1),
    variantId: z.string().nullable(),
    variantLabel: z.string().nullable(),
  }),
  z.object({
    ...base,
    kind: z.literal("kit"),
    kitId: z.string().min(1),
    summary: z.string(),
    selections: z
      .array(
        z.object({
          slotId: z.string().min(1),
          productId: z.string().min(1),
          variantId: z.string().nullable(),
          quantity: z.number().int().min(1),
        })
      )
      .min(1),
  }),
]);

/**
 * Parses a stored cart, dropping any line that doesn't match the current shape
 * (old versions, manual edits) instead of discarding the whole cart.
 */
export function parseStoredCart(raw: string | null): CartLine[] {
  if (!raw) return [];
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!Array.isArray(data)) return [];
  return data.flatMap((item) => {
    const parsed = lineSchema.safeParse(item);
    return parsed.success ? [parsed.data as CartLine] : [];
  });
}

export function readStoredCart(): CartLine[] {
  try {
    return parseStoredCart(window.localStorage.getItem(CART_STORAGE_KEY));
  } catch {
    return [];
  }
}

export function writeStoredCart(lines: CartLine[]): void {
  try {
    if (lines.length === 0) {
      window.localStorage.removeItem(CART_STORAGE_KEY);
    } else {
      window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(lines));
    }
  } catch {
    // Storage unavailable (private mode, quota): the cart lasts for this tab.
  }
}
