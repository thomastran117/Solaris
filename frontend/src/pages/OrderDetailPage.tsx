import { useNavigate, useParams } from "react-router-dom";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Package } from "lucide-react";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";
import SectionGlow from "../components/section/SectionGlow";
import SectionFade from "../components/section/SectionFade";
import OrderStatusTimeline from "../components/order/OrderStatusTimeline";
import TrackingPanel from "../components/order/TrackingPanel";
import TrackingTimeline from "../components/order/TrackingTimeline";
import PickupPanel from "../components/order/PickupPanel";
import { ordersApi } from "../api/orders";

const useAnims = () => {
  const reduced = useReducedMotion();
  const fadeInUp: Variants = reduced
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.5 } } };
  const stagger: Variants = reduced
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.04 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.08 } } };
  return { fadeInUp, stagger };
};

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { fadeInUp, stagger } = useAnims();

  const { data: order, isLoading, isError } = useQuery({
    queryKey: ["order", id],
    queryFn: () => ordersApi.get(id!).then((r) => r.data),
    enabled: !!id,
  });

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
                  <p className="text-xl font-extrabold text-white">{order.currency} {order.totalAmount.toFixed(2)}</p>
                  <p className="text-xs text-white/50 capitalize">{order.status.toLowerCase().replace(/_/g, " ")}</p>
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
          </motion.div>
        )}
        <SectionFade bottom />
      </div>
    </div>
  );
}
