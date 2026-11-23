"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import Link from "next/link";
import { ShoppingBag } from "lucide-react";
import { useAppSelector } from "../../stores/hooks";
import {
  cartSubtotal,
  groupByCurrency,
  selectCartCount,
  selectCartLines,
} from "../../stores/cartSlice";
import { formatMoney } from "../../lib/format";
import { Button } from "../ui/Button";
import { EmptyState } from "../ui/EmptyState";
import {
  Sheet,
  SheetBody,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "../ui/Sheet";
import { CartLineItem } from "./CartLineItem";

interface CartDrawerContextValue {
  open: boolean;
  setOpen: (open: boolean) => void;
  openCart: () => void;
}

const CartDrawerContext = createContext<CartDrawerContextValue | null>(null);

export function useCartDrawer(): CartDrawerContextValue {
  const context = useContext(CartDrawerContext);
  if (!context) {
    throw new Error("useCartDrawer must be used inside <CartDrawerProvider>");
  }
  return context;
}

/** Provides the cart drawer to the whole app and renders it once. */
export function CartDrawerProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const openCart = useCallback(() => setOpen(true), []);
  const value = useMemo(() => ({ open, setOpen, openCart }), [open, openCart]);
  return (
    <CartDrawerContext.Provider value={value}>
      {children}
      <CartDrawer open={open} onOpenChange={setOpen} />
    </CartDrawerContext.Provider>
  );
}

function CartDrawer({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const lines = useAppSelector(selectCartLines);
  const count = useAppSelector(selectCartCount);
  const groups = groupByCurrency(lines);
  const close = () => onOpenChange(false);
  // Checkout handles one currency; mixed carts pick a currency on /cart.
  const checkoutHref =
    groups.size === 1 ? `/checkout?currency=${[...groups.keys()][0]}` : "/cart";

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right">
        <SheetHeader>
          <SheetTitle className="text-lg font-semibold">
            Your cart{count > 0 && ` (${count})`}
          </SheetTitle>
          <SheetDescription className="sr-only">
            Items in your cart
          </SheetDescription>
        </SheetHeader>

        {lines.length === 0 ? (
          <SheetBody className="flex items-center">
            <EmptyState
              icon={ShoppingBag}
              title="Your cart is empty"
              description="Items you add will show up here."
              className="w-full border-0"
              action={
                <Button asChild onClick={close}>
                  <Link href="/browse">Browse products</Link>
                </Button>
              }
            />
          </SheetBody>
        ) : (
          <>
            <SheetBody className="py-0">
              <ul className="divide-y">
                {lines.map((line) => (
                  <CartLineItem
                    key={line.lineId}
                    line={line}
                    compact
                    onNavigate={close}
                  />
                ))}
              </ul>
            </SheetBody>
            <SheetFooter className="space-y-4">
              <dl className="space-y-1 text-sm">
                {[...groups].map(([currency, group]) => (
                  <div key={currency} className="flex justify-between">
                    <dt className="text-muted-foreground">
                      Subtotal{groups.size > 1 && ` (${currency})`}
                    </dt>
                    <dd className="font-semibold tabular-nums text-foreground">
                      {formatMoney(cartSubtotal(group), currency)}
                    </dd>
                  </div>
                ))}
              </dl>
              <p className="text-xs text-muted-foreground">
                Shipping, taxes and discounts are calculated at checkout.
              </p>
              <div className="grid gap-2">
                <Button asChild size="lg" onClick={close}>
                  <Link href={checkoutHref}>Checkout</Link>
                </Button>
                <Button asChild variant="outline" onClick={close}>
                  <Link href="/cart">View cart</Link>
                </Button>
              </div>
            </SheetFooter>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
