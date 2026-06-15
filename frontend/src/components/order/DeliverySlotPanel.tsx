import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, Loader2 } from "lucide-react";
import { ordersApi } from "../../api/orders";
import { DELIVERY_WINDOWS, type DeliveryWindow, type Order } from "../../types/order";
import {
  deliverySlotSchema,
  type DeliverySlotValues,
  MIN_DELIVERY_DATE,
  MAX_DELIVERY_DATE,
} from "../../schemas/order";
import DeliverySlotStatusBadge from "./DeliverySlotStatusBadge";

interface Props {
  order: Order;
}

const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20 transition-colors";
const labelCls = "block text-xs font-medium text-white/60 mb-1";

function extractMessage(err: unknown): string {
  if (err && typeof err === "object" && "apiMessage" in err) {
    const msg = (err as { apiMessage?: string }).apiMessage;
    if (msg) return msg;
  }
  return "Couldn't save your delivery slot. Please try again.";
}

const WINDOW_OPTIONS = Object.keys(DELIVERY_WINDOWS) as DeliveryWindow[];

export default function DeliverySlotPanel({ order }: Props) {
  const qc = useQueryClient();
  const editable = order.status === "RESERVED" || order.status === "PAID";

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<DeliverySlotValues>({
    resolver: zodResolver(deliverySlotSchema),
    defaultValues: {
      preferredDeliveryDate: order.preferredDeliveryDate ?? "",
      preferredDeliveryWindow: order.preferredDeliveryWindow ?? "MORNING",
    },
  });

  const mutation = useMutation({
    mutationFn: (values: DeliverySlotValues) =>
      ordersApi.setDeliverySlot(order.id, values).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["order", order.id] });
    },
  });

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
          Delivery Slot
        </p>
        {order.deliverySlotStatus && <DeliverySlotStatusBadge status={order.deliverySlotStatus} />}
      </div>

      {order.preferredDeliveryDate && (
        <div className="flex items-start gap-2">
          <CalendarClock className="h-4 w-4 text-sky-200 mt-0.5 shrink-0" />
          <p className="text-sm text-white">
            {new Date(order.preferredDeliveryDate + "T00:00:00").toLocaleDateString(undefined, {
              weekday: "long",
              day: "numeric",
              month: "long",
            })}
            {order.preferredDeliveryWindow && (
              <span className="text-white/60"> · {DELIVERY_WINDOWS[order.preferredDeliveryWindow]}</span>
            )}
          </p>
        </div>
      )}

      {order.deliverySlotStatus === "UNAVAILABLE" && (
        <p className="text-sm text-red-400">
          The seller can't make this slot. Please choose a different date below.
        </p>
      )}

      {editable ? (
        <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-3">
          <div>
            <label className={labelCls} htmlFor="preferredDeliveryDate">
              Preferred date
            </label>
            <input
              id="preferredDeliveryDate"
              type="date"
              min={MIN_DELIVERY_DATE}
              max={MAX_DELIVERY_DATE}
              className={inputCls}
              {...register("preferredDeliveryDate")}
            />
            {errors.preferredDeliveryDate && (
              <p className="mt-1 text-xs text-red-400">{errors.preferredDeliveryDate.message}</p>
            )}
          </div>

          <div>
            <label className={labelCls} htmlFor="preferredDeliveryWindow">
              Time window
            </label>
            <select id="preferredDeliveryWindow" className={inputCls} {...register("preferredDeliveryWindow")}>
              {WINDOW_OPTIONS.map((w) => (
                <option key={w} value={w} className="bg-slate-900">
                  {DELIVERY_WINDOWS[w]}
                </option>
              ))}
            </select>
            {errors.preferredDeliveryWindow && (
              <p className="mt-1 text-xs text-red-400">{errors.preferredDeliveryWindow.message}</p>
            )}
          </div>

          {mutation.isError && <p className="text-sm text-red-400">{extractMessage(mutation.error)}</p>}
          {mutation.isSuccess && !mutation.isPending && (
            <p className="text-sm text-green-400">Delivery slot saved.</p>
          )}

          <button
            type="submit"
            disabled={mutation.isPending}
            className="flex items-center gap-2 rounded-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 px-4 py-2 text-sm text-white font-medium transition"
          >
            {mutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CalendarClock className="h-4 w-4" />}
            {order.preferredDeliveryDate ? "Update slot" : "Request slot"}
          </button>
        </form>
      ) : (
        !order.preferredDeliveryDate && (
          <p className="text-sm text-white/60">
            A delivery slot can only be requested while your order is reserved or paid.
          </p>
        )
      )}
    </div>
  );
}
