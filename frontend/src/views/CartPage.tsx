"use client";

import Link from "next/link";
import { ArrowRight, ShoppingBag } from "lucide-react";
import { CartLineItem } from "../components/cart/CartLineItem";
import { OrderSummary } from "../components/checkout/OrderSummary";
import { Button } from "../components/ui/Button";
import { Container } from "../components/ui/Container";
import { EmptyState } from "../components/ui/EmptyState";
import { Skeleton } from "../components/ui/Skeleton";
import { useAppDispatch, useAppSelector } from "../stores/hooks";
import {
  clearCart,
  groupByCurrency,
  selectCartCount,
  selectCartHydrated,
  selectCartLines,
} from "../stores/cartSlice";

export default function CartPage() {
  const dispatch = useAppDispatch();
  const lines = useAppSelector(selectCartLines);
  const count = useAppSelector(selectCartCount);
  const hydrated = useAppSelector(selectCartHydrated);
  const groups = groupByCurrency(lines);

  return (
    <Container className="py-8 sm:py-12">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
          Your cart
          {hydrated && count > 0 && (
            <span className="ml-2 text-base font-normal text-muted-foreground">
              {count} {count === 1 ? "item" : "items"}
            </span>
          )}
        </h1>
        {hydrated && lines.length > 0 && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => dispatch(clearCart())}
          >
            Clear cart
          </Button>
        )}
      </div>

      <div className="mt-8">
        {!hydrated ? (
          <div
            className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_380px]"
            aria-busy
          >
            <Skeleton className="h-64 rounded-xl" />
            <Skeleton className="h-64 rounded-xl" />
          </div>
        ) : lines.length === 0 ? (
          <EmptyState
            icon={ShoppingBag}
            title="Your cart is empty"
            description="Browse the marketplace and add something you love."
            action={
              <Button asChild>
                <Link href="/browse">Browse products</Link>
              </Button>
            }
          />
        ) : (
          <div className="space-y-12">
            {[...groups].map(([currency, group]) => (
              <section
                key={currency}
                aria-label={groups.size > 1 ? `${currency} items` : undefined}
                className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_380px]"
              >
                <div>
                  {groups.size > 1 && (
                    <h2 className="mb-2 text-sm font-semibold text-foreground">
                      Items priced in {currency}
                    </h2>
                  )}
                  <ul className="divide-y rounded-xl border bg-surface px-4 sm:px-6">
                    {group.map((line) => (
                      <CartLineItem key={line.lineId} line={line} />
                    ))}
                  </ul>
                </div>
                <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
                  <OrderSummary
                    lines={group}
                    currency={currency}
                    showLines={false}
                    footnote="Shipping and tax are calculated at checkout."
                  />
                  <Button asChild size="lg" className="w-full">
                    <Link href={`/checkout?currency=${currency}`}>
                      {groups.size > 1
                        ? `Check out ${currency} items`
                        : "Checkout"}
                      <ArrowRight aria-hidden />
                    </Link>
                  </Button>
                  <Button asChild variant="link" className="w-full">
                    <Link href="/browse">Continue shopping</Link>
                  </Button>
                </aside>
              </section>
            ))}
          </div>
        )}
      </div>
    </Container>
  );
}
