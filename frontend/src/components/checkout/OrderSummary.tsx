"use client";

import { useQuery } from "@tanstack/react-query";
import { Package } from "lucide-react";
import { pricingApi } from "../../api/pricing";
import { cartSubtotal, lineTotal, type CartLine } from "../../stores/cartSlice";
import { toQuoteItems } from "../../lib/checkout";
import { formatMoney } from "../../lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "../ui/Card";
import { Skeleton } from "../ui/Skeleton";

interface Destination {
  country?: string;
  state?: string;
  postalCode?: string;
}

/**
 * Server-priced preview of the lines (POST /pricing/quote): promotions, coupon
 * and, once a destination is known, tax. Kit lines aren't quotable, so their
 * configured price is added as an estimate. The order is re-priced on creation.
 */
export function useCartQuote(
  lines: CartLine[],
  currency: string,
  couponCode?: string,
  destination?: Destination
) {
  const items = toQuoteItems(lines);
  const coupon = couponCode?.trim() || undefined;
  return useQuery({
    queryKey: [
      "pricing-quote",
      currency,
      items,
      coupon ?? null,
      destination ?? null,
    ],
    queryFn: () =>
      pricingApi
        .quote({
          items,
          currency,
          couponCode: coupon,
          destinationCountry: destination?.country?.toUpperCase() || undefined,
          // The quote accepts 2-letter codes only; omit full names rather than guess.
          destinationState: /^[A-Za-z]{2}$/.test(destination?.state ?? "")
            ? destination!.state!.toUpperCase()
            : undefined,
          destinationPostalCode: destination?.postalCode || undefined,
        })
        .then((r) => r.data),
    enabled: items.length > 0,
    placeholderData: (previous) => previous,
    staleTime: 30_000,
  });
}

function Row({
  label,
  value,
  strong,
  muted,
}: {
  label: string;
  value: string;
  strong?: boolean;
  muted?: boolean;
}) {
  return (
    <div className="flex justify-between gap-4">
      <dt className={muted ? "text-muted-foreground" : "text-foreground"}>
        {label}
      </dt>
      <dd
        className={
          strong
            ? "font-semibold tabular-nums text-foreground"
            : "tabular-nums text-foreground"
        }
      >
        {value}
      </dd>
    </div>
  );
}

export function OrderSummary({
  lines,
  currency,
  couponCode,
  destination,
  showLines = true,
  footnote = "Shipping is chosen after you place the order; tax is added for your delivery address.",
}: {
  lines: CartLine[];
  currency: string;
  couponCode?: string;
  destination?: Destination;
  showLines?: boolean;
  footnote?: string;
}) {
  const quote = useCartQuote(lines, currency, couponCode, destination);
  const kitLines = lines.filter((l) => l.kind === "kit");
  const kitEstimate = cartSubtotal(kitLines);
  const productSubtotal = cartSubtotal(
    lines.filter((l) => l.kind === "product")
  );
  const q = quote.data;
  const money = (n: number) => formatMoney(n, currency);
  const total = (q ? q.finalTotal : productSubtotal) + kitEstimate;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Order summary</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {showLines && (
          <ul className="divide-y text-sm">
            {lines.map((line) => (
              <li key={line.lineId} className="flex items-center gap-3 py-3">
                <span className="relative size-12 shrink-0 overflow-hidden rounded-md border bg-muted">
                  {line.thumbnailUrl ? (
                    <img
                      src={line.thumbnailUrl}
                      alt=""
                      className="size-full object-cover"
                    />
                  ) : (
                    <Package
                      className="m-auto mt-3 size-5 text-muted-foreground"
                      aria-hidden
                    />
                  )}
                  <span className="absolute -right-1 -top-1 flex size-5 items-center justify-center rounded-full bg-foreground text-[10px] font-semibold text-background">
                    {line.quantity}
                  </span>
                </span>
                <span className="min-w-0 flex-1">
                  <span className="line-clamp-1 font-medium text-foreground">
                    {line.name}
                  </span>
                  {line.kind === "kit" ? (
                    <span className="line-clamp-1 text-xs text-muted-foreground">
                      Kit · estimated
                    </span>
                  ) : (
                    line.variantLabel && (
                      <span className="line-clamp-1 text-xs text-muted-foreground">
                        {line.variantLabel}
                      </span>
                    )
                  )}
                </span>
                <span className="tabular-nums text-foreground">
                  {money(lineTotal(line))}
                </span>
              </li>
            ))}
          </ul>
        )}

        {quote.isPending && toQuoteItems(lines).length > 0 ? (
          <div className="space-y-2" aria-busy>
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-5 w-full" />
          </div>
        ) : (
          <dl className="space-y-2 border-t pt-4 text-sm">
            <Row
              label="Subtotal"
              value={money((q?.subtotal ?? productSubtotal) + kitEstimate)}
              muted
            />
            {!!q?.promotionSavings && (
              <Row
                label="Promotions"
                value={`−${money(q.promotionSavings)}`}
                muted
              />
            )}
            {!!q?.couponSavings && (
              <Row
                label={
                  q.appliedCouponCode
                    ? `Coupon (${q.appliedCouponCode})`
                    : "Coupon"
                }
                value={`−${money(q.couponSavings)}`}
                muted
              />
            )}
            {q && q.taxSource !== "NONE" && (
              <Row label="Estimated tax" value={money(q.taxAmount)} muted />
            )}
            <div className="border-t pt-2">
              <Row label="Estimated total" value={money(total)} strong />
            </div>
          </dl>
        )}

        {quote.isError && (
          <p className="text-xs text-warning">
            We couldn&apos;t refresh prices just now; totals use the prices
            shown when you added items.
          </p>
        )}
        {q?.warnings?.map((w) => (
          <p key={w} className="text-xs text-warning">
            {w}
          </p>
        ))}
        {kitLines.length > 0 && (
          <p className="text-xs text-muted-foreground">
            Kit prices are estimates until the order is placed.
          </p>
        )}
        {footnote && (
          <p className="text-xs text-muted-foreground">{footnote}</p>
        )}
      </CardContent>
    </Card>
  );
}
