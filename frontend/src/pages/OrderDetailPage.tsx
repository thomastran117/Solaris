import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { motion } from "framer-motion";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Loader2, Package, X } from "lucide-react";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";
import SectionGlow from "../components/section/SectionGlow";
import SectionFade from "../components/section/SectionFade";
import OrderStatusTimeline from "../components/order/OrderStatusTimeline";
import OrderStatusHistoryTimeline from "../components/order/OrderStatusHistoryTimeline";
import TrackingPanel from "../components/order/TrackingPanel";
import TrackingTimeline from "../components/order/TrackingTimeline";
import PickupPanel from "../components/order/PickupPanel";
import DeliverySlotPanel from "../components/order/DeliverySlotPanel";
import ShippingRatePanel from "../components/order/ShippingRatePanel";
import { ordersApi } from "../api/orders";
import { useOrderStream } from "../hooks/useOrderStream";
import { useAnims } from "../hooks/useAnims";
import DriverLocationIndicator from "../components/order/DriverLocationIndicator";

const CANCELLABLE = new Set(["RESERVED", "PAID", "PACKED"]);

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { fadeInUp, stagger } = useAnims();
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const { data: order, isLoading, isError } = useQuery({
    queryKey: ["order", id],
    queryFn: () => ordersApi.get(id!).then((r) => r.data),
    enabled: !!id,
  });

  const cancel = useMutation({
    mutationFn: () => ordersApi.cancel(id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["order", id] });
      navigate("/orders");
    },
    onError: () => {
      setCancelError("Failed to cancel order. Please try again.");
      cancel.reset();
    },
  });

  const { connected, driverLocation } = useOrderStream(id);

  const hasPickupReady = order?.items.some((i) => i.fulfillmentStatus === "PICKUP_READY") ?? false;

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />
      <div className="relative z-10 max-w-2xl mx-auto px-4 py-20">
        <SectionFade top />
        <SectionGlow variant="b" />

        <button
          onClick={() => navigate("/orders")}
          className="flex items-center gap-2 text-sm text-white/60 hover:text-white transition mb-8"
        >
          <ArrowLeft className="h-4 w-4" /> Back to Orders
        </button>

        {isLoading && (
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-24 animate-pulse" />
            ))}
          </div>
        )}

        {isError && (
          <p className="text-red-400 text-sm">Failed to load order. Please try again.</p>
        )}

        {order && (
          <motion.div initial="hidden" animate="visible" variants={stagger} className="space-y-6">
            {/* Header */}
            <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="bg-blue-500/15 border border-white/10 rounded-xl p-2">
                    <Package className="h-5 w-5 text-sky-200" />
                  </div>
                  <div>
                    <p className="text-xs text-white/50 uppercase tracking-wide">Order</p>
                    <p className="text-base font-mono font-bold text-white">#{order.id.slice(0, 8).toUpperCase()}</p>
                    <p className="text-xs text-white/50">{new Date(order.createdAt).toLocaleString()}</p>
                  </div>
                </div>
                <div className="text-right">
                  {connected && (
                    <div className="flex items-center justify-end gap-1.5 mb-1">
                      <span className="relative flex h-2 w-2">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75" />
                        <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500" />
                      </span>
                      <span className="text-xs text-blue-400 font-medium">Live</span>
                    </div>
                  )}
                  <p className="text-xl font-extrabold text-white">{order.currency} {order.totalAmount.toFixed(2)}</p>
                  <p className="text-xs text-white/50 capitalize">{order.status.toLowerCase().replace(/_/g, " ")}</p>
                  {CANCELLABLE.has(order.status) && (
                    <button
                      onClick={() => { setCancelError(null); setShowCancelModal(true); }}
                      className="mt-2 text-xs text-red-400 hover:text-red-300 transition underline underline-offset-2"
                    >
                      Cancel order
                    </button>
                  )}
                </div>
              </div>
            </motion.div>

            {/* Status timeline */}
            <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
              <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-4">Status</p>
              <OrderStatusTimeline
                status={order.status}
                fulfillmentMethod={order.fulfillmentMethod}
                hasPickupReady={hasPickupReady}
              />
            </motion.div>

            {/* Driver location */}
            {order.assignedDriverId && (
              <motion.div variants={fadeInUp}>
                <DriverLocationIndicator location={driverLocation} />
              </motion.div>
            )}

            {/* Tracking / Pickup */}
            {order.fulfillmentMethod === "DELIVERY" && order.trackingNumber && (
              <>
                <motion.div variants={fadeInUp}>
                  <TrackingPanel trackingNumber={order.trackingNumber} carrier={order.carrier} />
                </motion.div>
                <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
                  <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-4">Tracking Updates</p>
                  <TrackingTimeline orderId={order.id} />
                </motion.div>
              </>
            )}

            {order.fulfillmentMethod === "PICKUP" && (
              <motion.div variants={fadeInUp}>
                <PickupPanel order={order} />
              </motion.div>
            )}

            {/* Scheduled delivery slot */}
            {order.fulfillmentMethod === "DELIVERY" && (
              <motion.div variants={fadeInUp}>
                <DeliverySlotPanel order={order} />
              </motion.div>
            )}

            {/* Shipping rate selection — only before payment on a delivery order */}
            {order.fulfillmentMethod === "DELIVERY" && order.status === "RESERVED" && (
              <motion.div variants={fadeInUp}>
                <ShippingRatePanel order={order} />
              </motion.div>
            )}

            {/* Shipping address */}
            {order.fulfillmentMethod === "DELIVERY" && order.shipStreet && (
              <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
                <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-3">Shipping To</p>
                <div className="text-sm text-white/70 space-y-0.5">
                  {order.shipRecipientName && <p className="text-white font-medium">{order.shipRecipientName}</p>}
                  <p>{order.shipStreet}</p>
                  {order.shipStreet2 && <p>{order.shipStreet2}</p>}
                  <p>{order.shipCity}{order.shipState ? `, ${order.shipState}` : ""} {order.shipPostalCode}</p>
                  <p>{order.shipCountry}</p>
                </div>
              </motion.div>
            )}

            {/* Items */}
            <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
              <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-4">Items</p>
              <div className="space-y-3">
                {order.items.map((item) => (
                  <div key={item.id} className="flex items-center justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium text-white">{item.productName}</p>
                      {item.variantTitle && <p className="text-xs text-white/50">{item.variantTitle}</p>}
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm text-white">×{item.quantity}</p>
                      <p className="text-xs text-white/50">{order.currency} {(item.unitPrice * item.quantity).toFixed(2)}</p>
                    </div>
                  </div>
                ))}
              </div>
              <div className="border-t border-white/10 mt-4 pt-4 flex justify-between">
                <span className="text-sm text-white/60">Total</span>
                <span className="text-base font-extrabold text-white">{order.currency} {order.totalAmount.toFixed(2)}</span>
              </div>
            </motion.div>

            {/* History timeline */}
            <motion.div variants={fadeInUp}>
              <OrderStatusHistoryTimeline orderId={order.id} />
            </motion.div>
          </motion.div>
        )}
        <SectionFade bottom />
      </div>

      {/* Cancel confirmation modal */}
      {showCancelModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={() => setShowCancelModal(false)}>
          <div className="rounded-2xl border border-white/10 bg-slate-900 p-6 w-full max-w-sm space-y-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-white">Cancel this order?</h3>
              <button onClick={() => setShowCancelModal(false)} className="text-white/40 hover:text-white transition">
                <X className="h-4 w-4" />
              </button>
            </div>
            <p className="text-sm text-white/60">
              This will cancel your order and release any stock reservation.
              {order && ["RESERVED", "PAID", "PACKED"].includes(order.status) && " A full refund will be issued to your original payment method if payment was already captured."}
            </p>
            {cancelError && <p className="text-sm text-red-400">{cancelError}</p>}
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
