import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Truck } from "lucide-react";
import { ordersApi } from "../../api/orders";
import type { Order, ShippingRate } from "../../types/order";

interface Props {
  order: Order;
}

function extractMessage(err: unknown): string {
  if (err && typeof err === "object" && "apiMessage" in err) {
    const msg = (err as { apiMessage?: string }).apiMessage;
    if (msg) return msg;
  }
  return "Couldn't update your shipping selection. Please try again.";
}

function money(cents: number, currency: string): string {
  return `${currency} ${(cents / 100).toFixed(2)}`;
}

export default function ShippingRatePanel({ order }: Props) {
  const qc = useQueryClient();
  const [selectedRateId, setSelectedRateId] = useState<string | null>(order.shippingRateId);

  const ratesQuery = useQuery({
    queryKey: ["order", order.id, "shipping-rates"],
    queryFn: () => ordersApi.getShippingRates(order.id).then((r) => r.data),
  });

  const confirm = useMutation({
    mutationFn: (rateId: string) => ordersApi.confirmShippingRate(order.id, rateId).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["order", order.id] });
    },
  });

  const rates = ratesQuery.data ?? [];

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">Shipping</p>
        {order.shippingServiceName && (
          <span className="text-xs text-white/60">
            {order.shippingCarrier} · {order.shippingServiceName}
          </span>
        )}
      </div>

      {ratesQuery.isLoading && (
        <div className="space-y-2">
          {[1, 2, 3].map((i) => (
            <div key={i} className="rounded-xl border border-white/10 bg-white/[0.04] h-14 animate-pulse" />
          ))}
        </div>
      )}

      {ratesQuery.isError && (
        <p className="text-sm text-red-400">Couldn't load shipping options. Please try again.</p>
      )}

      {!ratesQuery.isLoading && !ratesQuery.isError && rates.length === 0 && (
        <p className="text-sm text-white/60">
          No live shipping options are available right now — standard shipping will be applied.
        </p>
      )}

      {rates.length > 0 && (
        <div className="space-y-2">
          {rates.map((rate: ShippingRate) => {
            const checked = selectedRateId === rate.rateId;
            return (
              <label
                key={rate.rateId}
                className={`flex items-center justify-between gap-3 rounded-xl border px-3 py-3 cursor-pointer transition-colors ${
                  checked
                    ? "border-white/20 bg-white/[0.08]"
                    : "border-white/10 bg-white/[0.04] hover:bg-white/[0.06]"
                }`}
              >
                <div className="flex items-center gap-3">
                  <input
                    type="radio"
                    name="shipping-rate"
                    value={rate.rateId}
                    checked={checked}
                    onChange={() => setSelectedRateId(rate.rateId)}
                    className="accent-blue-500"
                  />
                  <div>
                    <p className="text-sm font-medium text-white">
                      {rate.carrier} · {rate.serviceName}
                    </p>
                    <p className="text-xs text-white/50">
                      {rate.estimatedDays != null
                        ? `Est. ${rate.estimatedDays} day${rate.estimatedDays === 1 ? "" : "s"}`
                        : "Delivery estimate unavailable"}
                    </p>
                  </div>
                </div>
                <span className="text-sm font-extrabold text-white shrink-0">
                  {money(rate.totalCents, rate.currency)}
                </span>
              </label>
            );
          })}

          {confirm.isError && <p className="text-sm text-red-400">{extractMessage(confirm.error)}</p>}
          {confirm.isSuccess && !confirm.isPending && (
            <p className="text-sm text-green-400">Shipping selection saved.</p>
          )}

          <button
            type="button"
            disabled={!selectedRateId || confirm.isPending || selectedRateId === order.shippingRateId}
            onClick={() => selectedRateId && confirm.mutate(selectedRateId)}
            className="flex items-center gap-2 rounded-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 px-4 py-2 text-sm text-white font-medium transition"
          >
            {confirm.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Truck className="h-4 w-4" />}
            {order.shippingRateId ? "Update shipping" : "Confirm shipping"}
          </button>
        </div>
      )}

      {order.shippingCostCents > 0 && (
        <div className="border-t border-white/10 pt-3 flex justify-between text-sm">
          <span className="text-white/60">Shipping</span>
          <span className="text-white font-medium">
            {money(order.shippingCostCents, order.shippingRateCurrency ?? order.currency)}
          </span>
        </div>
      )}
    </div>
  );
}
