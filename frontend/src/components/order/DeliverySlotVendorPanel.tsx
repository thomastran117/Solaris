import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, CheckCircle, Loader2, X } from "lucide-react";
import { companyOrdersApi } from "../../api/companyOrders";
import { DELIVERY_WINDOWS, type CompanyOrder } from "../../types/order";
import DeliverySlotStatusBadge from "./DeliverySlotStatusBadge";

interface Props {
  order: CompanyOrder;
  companyId: string;
  onRefresh?: () => void;
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "apiMessage" in err) {
    const msg = (err as { apiMessage?: string }).apiMessage;
    if (msg) return msg;
  }
  return fallback;
}

export default function DeliverySlotVendorPanel({ order, companyId, onRefresh }: Props) {
  const qc = useQueryClient();
  const [showUnavailableModal, setShowUnavailableModal] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["company-orders", companyId] });
    qc.invalidateQueries({ queryKey: ["company-order", companyId, order.orderId] });
    onRefresh?.();
  };

  const confirm = useMutation({
    mutationFn: () => companyOrdersApi.confirmDeliverySlot(companyId, order.orderId),
    onSuccess: invalidate,
    onError: (err) => setError(extractErrorMessage(err, "Failed to confirm slot")),
  });

  const markUnavailable = useMutation({
    mutationFn: () =>
      companyOrdersApi.markDeliverySlotUnavailable(companyId, order.orderId, {
        reason: reason.trim() || undefined,
      }),
    onSuccess: () => {
      setShowUnavailableModal(false);
      setReason("");
      invalidate();
    },
    onError: (err) => setError(extractErrorMessage(err, "Failed to update slot")),
  });

  if (!order.deliverySlotStatus) return null;

  const canConfirm = order.deliverySlotStatus === "REQUESTED";
  // A vendor can still flag a previously-confirmed slot as unavailable if plans change.
  const canMarkUnavailable = order.deliverySlotStatus !== "UNAVAILABLE";

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
          Requested Delivery Slot
        </p>
        <DeliverySlotStatusBadge status={order.deliverySlotStatus} />
      </div>

      <div className="flex items-start gap-2">
        <CalendarClock className="h-4 w-4 text-sky-200 mt-0.5 shrink-0" />
        <p className="text-sm text-white">
          {order.preferredDeliveryDate
            ? new Date(order.preferredDeliveryDate + "T00:00:00").toLocaleDateString(undefined, {
                weekday: "long",
                day: "numeric",
                month: "long",
              })
            : "No date set"}
          {order.preferredDeliveryWindow && (
            <span className="text-white/60"> · {DELIVERY_WINDOWS[order.preferredDeliveryWindow]}</span>
          )}
        </p>
      </div>

      {error && <p className="text-sm text-red-400">{error}</p>}

      {(canConfirm || canMarkUnavailable) && (
        <div className="flex flex-wrap items-center gap-3">
          {canConfirm && (
            <button
              onClick={() => { setError(null); confirm.mutate(); }}
              disabled={confirm.isPending}
              className="flex items-center gap-2 rounded-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 px-4 py-2 text-sm text-white font-medium transition"
            >
              {confirm.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle className="h-4 w-4" />}
              Confirm Slot
            </button>
          )}
          {canMarkUnavailable && (
            <button
              onClick={() => { setError(null); setShowUnavailableModal(true); }}
              className="flex items-center gap-2 rounded-full border border-white/20 hover:bg-white/10 px-4 py-2 text-sm text-white/70 hover:text-white transition"
            >
              <X className="h-4 w-4" /> Mark Unavailable
            </button>
          )}
        </div>
      )}

      {showUnavailableModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="rounded-2xl border border-white/10 bg-slate-900 p-6 w-full max-w-sm space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-white">Mark slot unavailable?</h3>
              <button onClick={() => setShowUnavailableModal(false)} className="text-white/40 hover:text-white transition">
                <X className="h-4 w-4" />
              </button>
            </div>
            <p className="text-sm text-white/60">
              The customer will be emailed that you can't make their requested slot.
            </p>
            <textarea
              className="w-full rounded-xl bg-white/[0.06] border border-white/10 px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20"
              placeholder="Reason (optional)"
              rows={3}
              maxLength={500}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            {error && <p className="text-sm text-red-400">{error}</p>}
            <div className="flex gap-3">
              <button
                onClick={() => setShowUnavailableModal(false)}
                className="flex-1 rounded-full border border-white/20 py-2 text-sm text-white/70 hover:bg-white/10 transition"
              >
                Go back
              </button>
              <button
                onClick={() => markUnavailable.mutate()}
                disabled={markUnavailable.isPending}
                className="flex-1 rounded-full bg-red-600 hover:bg-red-500 disabled:opacity-50 py-2 text-sm text-white font-medium transition"
              >
                {markUnavailable.isPending ? <Loader2 className="h-4 w-4 animate-spin mx-auto" /> : "Confirm"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
