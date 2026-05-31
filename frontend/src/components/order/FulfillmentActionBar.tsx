import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, Truck, Package, CheckCircle, MapPin, X } from "lucide-react";
import { companyOrdersApi } from "../../api/companyOrders";
import type { CompanyOrder } from "../../types/order";

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "apiMessage" in err) {
    const msg = (err as { apiMessage?: string }).apiMessage;
    if (msg) return msg;
  }
  return fallback;
}

interface Props {
  order: CompanyOrder;
  companyId: string;
  onRefresh?: () => void;
}

const CANCELLABLE = new Set(["RESERVED", "PAID", "PACKED"]);

export default function FulfillmentActionBar({ order, companyId, onRefresh }: Props) {
  const [showShipModal, setShowShipModal] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [trackingNumber, setTrackingNumber] = useState("");
  const [carrier, setCarrier] = useState("");
  const [error, setError] = useState<string | null>(null);
  const qc = useQueryClient();

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["company-orders", companyId] });
    qc.invalidateQueries({ queryKey: ["company-order", companyId, order.orderId] });
    onRefresh?.();
  };

  const pack = useMutation({
    mutationFn: () => companyOrdersApi.pack(companyId, order.orderId),
    onSuccess: invalidate,
    onError: (err) => setError(extractErrorMessage(err, "Failed to mark as packed")),
  });

  const ship = useMutation({
    mutationFn: () => companyOrdersApi.ship(companyId, order.orderId, { trackingNumber, carrier }),
    onSuccess: () => { setShowShipModal(false); invalidate(); },
    onError: (err) => setError(extractErrorMessage(err, "Failed to mark as shipped")),
  });

  const pickupReady = useMutation({
    mutationFn: () => companyOrdersApi.markPickupReady(companyId, order.orderId),
    onSuccess: invalidate,
    onError: (err) => setError(extractErrorMessage(err, "Failed to mark pickup ready")),
  });

  const deliver = useMutation({
    mutationFn: () => companyOrdersApi.deliver(companyId, order.orderId),
    onSuccess: invalidate,
    onError: (err) => setError(extractErrorMessage(err, "Failed to mark as delivered")),
  });

  const cancel = useMutation({
    mutationFn: () => companyOrdersApi.cancel(companyId, order.orderId),
    onSuccess: () => { setShowCancelModal(false); invalidate(); },
    onError: (err) => setError(extractErrorMessage(err, "Failed to cancel order")),
  });

  const isLoading = pack.isPending || ship.isPending || pickupReady.isPending || deliver.isPending || cancel.isPending;
  const status = order.orderStatus;
  const method = order.fulfillmentMethod;

  const hasPickupReadyItems = order.items.some((i) => i.fulfillmentStatus === "PICKUP_READY");

  const renderPrimaryAction = () => {
    if (method === "DELIVERY") {
      if (status === "PAID") return (
        <Btn icon={<Package />} onClick={() => pack.mutate()} loading={pack.isPending}>Mark Packed</Btn>
      );
      if (status === "PACKED") return (
        <Btn icon={<Truck />} onClick={() => { setError(null); setShowShipModal(true); }}>Mark Shipped</Btn>
      );
      if (status === "SHIPPED" || status === "PARTIALLY_FULFILLED") return (
        <Btn icon={<CheckCircle />} onClick={() => deliver.mutate()} loading={deliver.isPending}>Mark Delivered</Btn>
      );
    }

    if (method === "PICKUP") {
      if (status === "PAID") return (
        <Btn icon={<Package />} onClick={() => pack.mutate()} loading={pack.isPending}>Mark Packed</Btn>
      );
      if (status === "PACKED" && !hasPickupReadyItems) return (
        <Btn icon={<MapPin />} onClick={() => pickupReady.mutate()} loading={pickupReady.isPending}>Mark Pickup Ready</Btn>
      );
      if (status === "PACKED" && hasPickupReadyItems) return (
        <Btn icon={<CheckCircle />} onClick={() => deliver.mutate()} loading={deliver.isPending}>Mark Delivered (Collected)</Btn>
      );
    }

    return null;
  };

  const primaryAction = renderPrimaryAction();
  const canCancel = CANCELLABLE.has(status);

  if (!primaryAction && !canCancel) return null;

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-4 flex flex-col gap-3">
      {error && <p className="text-sm text-red-400">{error}</p>}
      <div className="flex flex-wrap items-center gap-3">
        {isLoading ? (
          <Loader2 className="h-5 w-5 text-white/40 animate-spin" />
        ) : (
          <>
            {primaryAction}
            {canCancel && (
              <button
                onClick={() => { setError(null); setShowCancelModal(true); }}
                className="flex items-center gap-2 rounded-full border border-white/20 hover:bg-white/10 px-4 py-2 text-sm text-white/70 hover:text-white transition"
              >
                <X className="h-4 w-4" /> Cancel Order
              </button>
            )}
          </>
        )}
      </div>

      {/* Ship modal */}
      {showShipModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="rounded-2xl border border-white/10 bg-slate-900 p-6 w-full max-w-sm space-y-4">
            <h3 className="text-lg font-semibold text-white">Ship Order</h3>
            <input
              className="w-full rounded-xl bg-white/[0.06] border border-white/10 px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20"
              placeholder="Tracking number *"
              value={trackingNumber}
              onChange={(e) => setTrackingNumber(e.target.value)}
            />
            <input
              className="w-full rounded-xl bg-white/[0.06] border border-white/10 px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20"
              placeholder="Carrier (e.g. UPS, FEDEX)"
              value={carrier}
              onChange={(e) => setCarrier(e.target.value)}
            />
            {error && <p className="text-sm text-red-400">{error}</p>}
            <div className="flex gap-3">
              <button
                onClick={() => setShowShipModal(false)}
                className="flex-1 rounded-full border border-white/20 py-2 text-sm text-white/70 hover:bg-white/10 transition"
              >
                Cancel
              </button>
              <button
                onClick={() => ship.mutate()}
                disabled={!trackingNumber || ship.isPending}
                className="flex-1 rounded-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 py-2 text-sm text-white font-medium transition"
              >
                {ship.isPending ? <Loader2 className="h-4 w-4 animate-spin mx-auto" /> : "Confirm Ship"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Cancel confirmation modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="rounded-2xl border border-white/10 bg-slate-900 p-6 w-full max-w-sm space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-white">Cancel this order?</h3>
              <button onClick={() => setShowCancelModal(false)} className="text-white/40 hover:text-white transition">
                <X className="h-4 w-4" />
              </button>
            </div>
            <p className="text-sm text-white/60">
              This will cancel the order, restore all stock, and issue a full refund to the customer if payment was already captured.
            </p>
            {error && <p className="text-sm text-red-400">{error}</p>}
            <div className="flex gap-3">
              <button
                onClick={() => setShowCancelModal(false)}
                className="flex-1 rounded-full border border-white/20 py-2 text-sm text-white/70 hover:bg-white/10 transition"
              >
                Go back
              </button>
              <button
                onClick={() => cancel.mutate()}
                disabled={cancel.isPending}
                className="flex-1 rounded-full bg-red-600 hover:bg-red-500 disabled:opacity-50 py-2 text-sm text-white font-medium transition"
              >
                {cancel.isPending ? <Loader2 className="h-4 w-4 animate-spin mx-auto" /> : "Confirm cancel"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function Btn({
  icon,
  onClick,
  loading,
  children,
}: {
  icon: React.ReactNode;
  onClick: () => void;
  loading?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      disabled={loading}
      className="flex items-center gap-2 rounded-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 px-4 py-2 text-sm text-white font-medium transition"
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <span className="h-4 w-4 [&>svg]:h-4 [&>svg]:w-4">{icon}</span>}
      {children}
    </button>
  );
}
