"use client";

import { ShoppingBag } from "lucide-react";
import { useAppSelector } from "../../stores/hooks";
import { selectCartCount, selectCartHydrated } from "../../stores/cartSlice";
import { IconButton } from "../ui/IconButton";
import { useCartDrawer } from "./CartDrawer";

/** Header cart button. The count appears once the stored cart has loaded. */
export function CartButton() {
  const { openCart } = useCartDrawer();
  const count = useAppSelector(selectCartCount);
  const hydrated = useAppSelector(selectCartHydrated);
  const shown = hydrated && count > 0;

  return (
    <IconButton
      aria-label={
        shown ? `Cart, ${count} ${count === 1 ? "item" : "items"}` : "Cart"
      }
      className="relative"
      onClick={openCart}
    >
      <ShoppingBag aria-hidden />
      {shown && (
        <span
          aria-hidden
          className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1 text-[11px] font-semibold tabular-nums text-primary-foreground"
        >
          {count > 99 ? "99+" : count}
        </span>
      )}
    </IconButton>
  );
}
