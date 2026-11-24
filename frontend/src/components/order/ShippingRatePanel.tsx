import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Truck } from "lucide-react";
import { ordersApi } from "../../api/orders";
import type { Order, ShippingRate } from "../../types/order";
import { apiErrorMessage } from "../../lib/apiError";
import { formatMoney } from "../../lib/format";
import { Button } from "../ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "../ui/Card";
import { RadioCard } from "../ui/RadioCard";
import { Skeleton } from "../ui/Skeleton";

interface Props {
  order: Order;
  /** Called with the updated order after a rate is confirmed (checkout advances on it). */
  onConfirmed?: (order: Order) => void;
}

const money = (cents: number, currency: string) =>
  formatMoney(cents / 100, currency);

function estimate(rate: ShippingRate): string {
  return rate.estimatedDays != null
    ? `Est. ${rate.estimatedDays} day${rate.estimatedDays === 1 ? "" : "s"}`
    : "Delivery estimate unavailable";
}

export default function ShippingRatePanel({ order, onConfirmed }: Props) {
  const qc = useQueryClient();
  const [selectedRateId, setSelectedRateId] = useState<string | null>(
    order.shippingRateId
  );

  // Rates are only meaningful (and only fetchable from the backend) for a RESERVED delivery
  // order. Guard at the component level so it stays correct regardless of how it's mounted.
  const rateable =
    order.status === "RESERVED" && order.fulfillmentMethod === "DELIVERY";

  // Separate top-level key (not nested under ["order", id]) so invalidating the order detail
  // query after a confirm does not prefix-match and trigger a redundant rate re-fetch — each
  // refetch spends the per-user rate-limit budget and can cost a billable EasyPost call.
  const ratesQuery = useQuery({
    queryKey: ["shipping-rates", order.id],
    queryFn: () => ordersApi.getShippingRates(order.id).then((r) => r.data),
    enabled: rateable,
  });

  const confirm = useMutation({
    mutationFn: (rateId: string) =>
      ordersApi.confirmShippingRate(order.id, rateId).then((r) => r.data),
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ["order", order.id] });
      onConfirmed?.(updated);
    },
  });

  // After every hook, so the hook order is identical whether or not the order is rateable.
  if (!rateable) {
    return null;
  }

  const rates = ratesQuery.data ?? [];

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-3">
        <CardTitle>Shipping</CardTitle>
        {order.shippingServiceName && (
          <span className="text-sm text-muted-foreground">
            {order.shippingCarrier} · {order.shippingServiceName}
          </span>
        )}
      </CardHeader>
      <CardContent className="space-y-4">
        {ratesQuery.isLoading && (
          <div className="space-y-2" aria-busy>
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-16 rounded-xl" />
            ))}
          </div>
        )}

        {ratesQuery.isError && (
          <p role="alert" className="text-sm text-danger">
            Couldn&apos;t load shipping options. Please try again.
          </p>
        )}

        {!ratesQuery.isLoading && !ratesQuery.isError && rates.length === 0 && (
          <p className="text-sm text-muted-foreground">
            No live shipping options are available right now — standard shipping
            will be applied.
          </p>
        )}

        {rates.length > 0 && (
          <fieldset className="space-y-2">
            <legend className="sr-only">Shipping options</legend>
            {rates.map((rate) => (
              <RadioCard
                key={rate.rateId}
                name="shipping-rate"
                value={rate.rateId}
                checked={selectedRateId === rate.rateId}
                onChange={() => setSelectedRateId(rate.rateId)}
                title={`${rate.carrier} · ${rate.serviceName}`}
                description={estimate(rate)}
                aside={money(rate.totalCents, rate.currency)}
              />
            ))}

            {confirm.isError && (
              <p role="alert" className="text-sm text-danger">
                {apiErrorMessage(
                  confirm.error,
                  "Couldn't update your shipping selection. Please try again."
                )}
              </p>
            )}
            {confirm.isSuccess && !confirm.isPending && (
              <p role="status" className="text-sm text-success">
                Shipping selection saved.
              </p>
            )}

            <Button
              className="mt-2"
              disabled={
                !selectedRateId || selectedRateId === order.shippingRateId
              }
              loading={confirm.isPending}
              onClick={() => selectedRateId && confirm.mutate(selectedRateId)}
            >
              {!confirm.isPending && <Truck aria-hidden />}
              {order.shippingRateId ? "Update shipping" : "Confirm shipping"}
            </Button>
          </fieldset>
        )}

        {order.shippingCostCents > 0 && (
          <div className="flex justify-between border-t pt-3 text-sm">
            <span className="text-muted-foreground">Shipping</span>
            <span className="font-medium tabular-nums text-foreground">
              {money(
                order.shippingCostCents,
                order.shippingRateCurrency ?? order.currency
              )}
            </span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
