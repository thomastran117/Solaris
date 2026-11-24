import { describe, expect, it } from "vitest";
import reducer, {
  addLine,
  cartLineId,
  groupByCurrency,
  hydrateCart,
  removeLine,
  removeLines,
  selectCartCount,
  setQuantity,
  type CartLine,
} from "./cartSlice";
import { parseStoredCart } from "./cartPersistence";

const product = {
  kind: "product" as const,
  productId: "p1",
  variantId: "v1",
  variantLabel: "Black",
  name: "Headphones",
  thumbnailUrl: null,
  price: 50,
  currency: "USD",
  quantity: 1,
  marketplaceId: "mp",
  companyId: "c1",
};

const kit = {
  kind: "kit" as const,
  kitId: "k1",
  selections: [
    { slotId: "b", productId: "lens", variantId: null, quantity: 1 },
    { slotId: "a", productId: "body", variantId: "v", quantity: 1 },
  ],
  summary: "Body · Lens",
  name: "Starter kit",
  thumbnailUrl: null,
  price: 900,
  currency: "USD",
  quantity: 1,
  marketplaceId: "mp",
  companyId: "c1",
};

const empty = reducer(undefined, { type: "init" });

describe("cartSlice", () => {
  it("merges the same product and variant into one line", () => {
    let state = reducer(empty, addLine(product));
    state = reducer(state, addLine({ ...product, quantity: 2, price: 45 }));
    expect(state.lines).toHaveLength(1);
    expect(state.lines[0]).toMatchObject({ quantity: 3, price: 45 });
    expect(selectCartCount({ cart: state })).toBe(3);
  });

  it("keeps different variants and kit configurations apart", () => {
    let state = reducer(empty, addLine(product));
    state = reducer(state, addLine({ ...product, variantId: "v2" }));
    state = reducer(state, addLine(kit));
    state = reducer(
      state,
      addLine({
        ...kit,
        selections: [
          { slotId: "a", productId: "other", variantId: null, quantity: 1 },
        ],
      })
    );
    expect(state.lines).toHaveLength(4);
  });

  it("identifies a kit configuration regardless of slot order", () => {
    expect(cartLineId(kit)).toBe(
      cartLineId({ ...kit, selections: [...kit.selections].reverse() })
    );
  });

  it("clamps quantities to the order limits", () => {
    let state = reducer(empty, addLine({ ...product, quantity: 5000 }));
    expect(state.lines[0].quantity).toBe(999);
    const id = state.lines[0].lineId;
    state = reducer(state, setQuantity({ lineId: id, quantity: 2.7 }));
    expect(state.lines[0].quantity).toBe(2);
    state = reducer(state, setQuantity({ lineId: id, quantity: 0 }));
    expect(state.lines).toHaveLength(0);
  });

  it("removes single lines and the lines an order was placed for", () => {
    let state = reducer(empty, addLine(product));
    state = reducer(state, addLine(kit));
    state = reducer(state, addLine({ ...product, productId: "p2" }));
    const [a, b] = state.lines.map((l) => l.lineId);
    state = reducer(state, removeLine(a));
    expect(state.lines.map((l) => l.lineId)).not.toContain(a);
    state = reducer(state, removeLines([b]));
    expect(state.lines).toHaveLength(1);
  });

  it("marks the cart hydrated when the stored copy loads", () => {
    expect(empty.hydrated).toBe(false);
    const state = reducer(empty, hydrateCart([]));
    expect(state.hydrated).toBe(true);
  });

  it("groups lines by currency for checkout", () => {
    let state = reducer(empty, addLine(product));
    state = reducer(
      state,
      addLine({ ...product, productId: "p2", currency: "CAD" })
    );
    const groups = groupByCurrency(state.lines);
    expect([...groups.keys()]).toEqual(["USD", "CAD"]);
  });
});

describe("parseStoredCart", () => {
  it("round-trips valid lines", () => {
    let state = reducer(empty, addLine(product));
    state = reducer(state, addLine(kit));
    expect(parseStoredCart(JSON.stringify(state.lines))).toEqual(state.lines);
  });

  it("drops invalid lines instead of the whole cart", () => {
    const good = reducer(empty, addLine(product)).lines[0];
    const stored = JSON.stringify([
      good,
      { ...good, lineId: "bad", quantity: 0 },
      { kind: "mystery" },
      "nope",
    ]);
    expect(parseStoredCart(stored)).toEqual([good] as CartLine[]);
  });

  it("returns an empty cart for missing or corrupt storage", () => {
    expect(parseStoredCart(null)).toEqual([]);
    expect(parseStoredCart("{not json")).toEqual([]);
    expect(parseStoredCart(JSON.stringify({ lines: [] }))).toEqual([]);
  });
});
