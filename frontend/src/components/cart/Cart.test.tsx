import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { renderWithProviders } from "../../test/renderWithProviders";
import { CART_STORAGE_KEY } from "../../stores/cartPersistence";
import { addLine, type ProductCartLine } from "../../stores/cartSlice";
import { CartButton } from "./CartButton";
import { CartPersistence } from "./CartPersistence";

const stored: ProductCartLine[] = [
  {
    kind: "product",
    lineId: "p:p1:-",
    productId: "p1",
    variantId: null,
    variantLabel: null,
    name: "Aurora Headphones",
    thumbnailUrl: null,
    price: 199.99,
    currency: "USD",
    quantity: 2,
    marketplaceId: "mp",
    companyId: "c1",
  },
];

function renderCart() {
  // renderWithProviders supplies the CartDrawerProvider, as the app does.
  return renderWithProviders(
    <>
      <CartButton />
      <CartPersistence />
    </>
  );
}

afterEach(() => {
  window.localStorage.clear();
});

describe("cart", () => {
  it("loads the stored cart after mount and shows the count", async () => {
    window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(stored));
    const { store } = renderCart();
    expect(store.getState().cart.hydrated).toBe(true);
    expect(
      await screen.findByRole("button", { name: "Cart, 2 items" })
    ).toBeInTheDocument();
  });

  it("persists changes back to storage", () => {
    const { store } = renderCart();
    act(() => {
      const { lineId: _lineId, ...line } = stored[0];
      store.dispatch(addLine({ ...line, quantity: 1 }));
    });
    const saved = JSON.parse(
      window.localStorage.getItem(CART_STORAGE_KEY) ?? "[]"
    );
    expect(saved).toHaveLength(1);
    expect(saved[0]).toMatchObject({ productId: "p1", quantity: 1 });
  });

  it("follows changes made in another tab", () => {
    const { store } = renderCart();
    act(() => {
      window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(stored));
      window.dispatchEvent(
        new StorageEvent("storage", { key: CART_STORAGE_KEY })
      );
    });
    expect(store.getState().cart.lines).toHaveLength(1);
  });

  it("opens the drawer with lines, subtotal and checkout", async () => {
    window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(stored));
    const user = userEvent.setup();
    renderCart();
    await user.click(
      await screen.findByRole("button", { name: /Cart, 2 items/ })
    );
    const drawer = await screen.findByRole("dialog", {
      name: /Your cart \(2\)/,
    });
    expect(within(drawer).getByText("Aurora Headphones")).toBeInTheDocument();
    expect(within(drawer).getAllByText("$399.98")).not.toHaveLength(0);
    expect(
      within(drawer).getByRole("link", { name: "Checkout" })
    ).toHaveAttribute("href", "/checkout?currency=USD");

    await user.click(
      within(drawer).getByRole("button", { name: "Increase quantity" })
    );
    // The open drawer hides the page behind it from assistive tech.
    expect(
      screen.getByRole("button", { name: "Cart, 3 items", hidden: true })
    ).toBeInTheDocument();

    await user.click(
      within(drawer).getByRole("button", { name: "Remove Aurora Headphones" })
    );
    expect(within(drawer).getByText("Your cart is empty")).toBeInTheDocument();
  });

  it("drops corrupt stored data without breaking", () => {
    window.localStorage.setItem(CART_STORAGE_KEY, "{oops");
    const { store } = renderCart();
    expect(store.getState().cart).toMatchObject({ hydrated: true, lines: [] });
    expect(screen.getByRole("button", { name: "Cart" })).toBeInTheDocument();
  });
});
