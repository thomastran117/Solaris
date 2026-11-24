import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

/** Upper bound accepted by CreateOrderRequest.OrderItemRequest.quantity. */
export const MAX_LINE_QUANTITY = 999;

interface CartLineBase {
  /** Stable identity: the same product/variant (or kit configuration) merges. */
  lineId: string;
  name: string;
  thumbnailUrl: string | null;
  /** Unit price when added: display only. The order is re-priced by the server. */
  price: number;
  currency: string;
  quantity: number;
  marketplaceId: string;
  companyId: string;
}

export interface ProductCartLine extends CartLineBase {
  kind: "product";
  productId: string;
  variantId: string | null;
  variantLabel: string | null;
}

export interface KitSelection {
  slotId: string;
  productId: string;
  variantId: string | null;
  quantity: number;
}

export interface KitCartLine extends CartLineBase {
  kind: "kit";
  kitId: string;
  selections: KitSelection[];
  /** Human-readable configuration, e.g. "Body: Mirrorless X · Lens: 35mm". */
  summary: string;
}

export type CartLine = ProductCartLine | KitCartLine;

type NewLine = Omit<ProductCartLine, "lineId"> | Omit<KitCartLine, "lineId">;

export interface CartState {
  lines: CartLine[];
  /** False until the persisted cart has been read after mount. */
  hydrated: boolean;
}

const initialState: CartState = { lines: [], hydrated: false };

export function cartLineId(line: NewLine): string {
  if (line.kind === "product") {
    return `p:${line.productId}:${line.variantId ?? "-"}`;
  }
  const config = [...line.selections]
    .sort((a, b) => a.slotId.localeCompare(b.slotId))
    .map(
      (s) => `${s.slotId}=${s.productId}/${s.variantId ?? "-"}x${s.quantity}`
    )
    .join(",");
  return `k:${line.kitId}:${config}`;
}

const clampQuantity = (quantity: number) =>
  Math.min(MAX_LINE_QUANTITY, Math.max(1, Math.floor(quantity)));

const cartSlice = createSlice({
  name: "cart",
  initialState,
  reducers: {
    addLine: (state, action: PayloadAction<NewLine>) => {
      const lineId = cartLineId(action.payload);
      const existing = state.lines.find((l) => l.lineId === lineId);
      if (existing) {
        existing.quantity = clampQuantity(
          existing.quantity + action.payload.quantity
        );
        // Keep the latest display price and image.
        existing.price = action.payload.price;
        existing.thumbnailUrl = action.payload.thumbnailUrl;
        return;
      }
      state.lines.push({
        ...action.payload,
        quantity: clampQuantity(action.payload.quantity),
        lineId,
      } as CartLine);
    },
    setQuantity: (
      state,
      action: PayloadAction<{ lineId: string; quantity: number }>
    ) => {
      const line = state.lines.find((l) => l.lineId === action.payload.lineId);
      if (!line) return;
      if (action.payload.quantity <= 0) {
        state.lines = state.lines.filter((l) => l !== line);
      } else {
        line.quantity = clampQuantity(action.payload.quantity);
      }
    },
    removeLine: (state, action: PayloadAction<string>) => {
      state.lines = state.lines.filter((l) => l.lineId !== action.payload);
    },
    /** Drops the lines an order was just created from. */
    removeLines: (state, action: PayloadAction<string[]>) => {
      const ids = new Set(action.payload);
      state.lines = state.lines.filter((l) => !ids.has(l.lineId));
    },
    clearCart: (state) => {
      state.lines = [];
    },
    /** Replaces the cart with the persisted copy (on mount and on cross-tab change). */
    hydrateCart: (state, action: PayloadAction<CartLine[]>) => {
      state.lines = action.payload;
      state.hydrated = true;
    },
  },
});

export const {
  addLine,
  setQuantity,
  removeLine,
  removeLines,
  clearCart,
  hydrateCart,
} = cartSlice.actions;
export default cartSlice.reducer;

type WithCart = { cart: CartState };

export const selectCartLines = (s: WithCart) => s.cart.lines;
export const selectCartHydrated = (s: WithCart) => s.cart.hydrated;
export const selectCartCount = (s: WithCart) =>
  s.cart.lines.reduce((n, l) => n + l.quantity, 0);

/** Lines grouped by currency; an order is placed in a single currency. */
export function groupByCurrency(lines: CartLine[]): Map<string, CartLine[]> {
  const groups = new Map<string, CartLine[]>();
  for (const line of lines) {
    const list = groups.get(line.currency) ?? [];
    list.push(line);
    groups.set(line.currency, list);
  }
  return groups;
}

export const lineTotal = (line: CartLine) => line.price * line.quantity;

export const cartSubtotal = (lines: CartLine[]) =>
  lines.reduce((sum, l) => sum + lineTotal(l), 0);
