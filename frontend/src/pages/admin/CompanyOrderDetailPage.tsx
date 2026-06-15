import { useNavigate, useParams } from "react-router-dom";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Package, MapPin } from "lucide-react";
import NavyGridGlowBackground from "../../components/layout/NavyGridGlowBackground";
import SectionGlow from "../../components/section/SectionGlow";
import SectionFade from "../../components/section/SectionFade";
import OrderStatusTimeline from "../../components/order/OrderStatusTimeline";
import FulfillmentActionBar from "../../components/order/FulfillmentActionBar";
import DeliverySlotVendorPanel from "../../components/order/DeliverySlotVendorPanel";
import PickupPanel from "../../components/order/PickupPanel";
import { companyOrdersApi } from "../../api/companyOrders";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";


export default function CompanyOrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const { fadeInUp, stagger } = useAnims();
  const { companyId } = useSelector((state: RootState) => state.auth);

  const { data: order, isLoading, isError, refetch } = useQuery({
    queryKey: ["company-order", companyId, orderId],
    queryFn: () => companyOrdersApi.get(companyId!, orderId!).then((r) => r.data),
    enabled: !!companyId && !!orderId,
  });

  const hasPickupReady = order?.items.some((i) => i.fulfillmentStatus === "PICKUP_READY") ?? false;

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />
      <div className="relative z-10 max-w-2xl mx-auto px-4 py-20">
        <SectionFade top />
        <SectionGlow variant="c" />

        <button
          onClick={() => navigate("/admin/orders")}
          className="flex items-center gap-2 text-sm text-white/60 hover:text-white transition mb-8"
        >
          <ArrowLeft className="h-4 w-4" /> Back to Orders
        </button>

        {isLoading && (
          <div className="space-y-4">
            {[1, 2, 3].map((i) => <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-24 animate-pulse" />)}
          </div>
        )}

        {isError && <p className="text-red-400 text-sm">Failed to load order.</p>}

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
                    <p className="text-base font-mono font-bold text-white">#{order.orderId.slice(0, 8).toUpperCase()}</p>
                    <p className="text-xs text-white/50">{new Date(order.createdAt).toLocaleString()}</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-xl font-extrabold text-white">{order.currency} {order.companyItemsTotal.toFixed(2)}</p>
                  <p className="text-xs text-white/50 capitalize">{order.orderStatus.toLowerCase().replace(/_/g, " ")}</p>
                </div>
              </div>
            </motion.div>

            {/* Fulfillment action bar */}
            <motion.div variants={fadeInUp}>
              <FulfillmentActionBar order={order} companyId={companyId!} onRefresh={refetch} />
            </motion.div>

            {/* Delivery slot (DELIVERY orders with a requested slot) */}
            {order.fulfillmentMethod === "DELIVERY" && order.deliverySlotStatus && (
              <motion.div variants={fadeInUp}>
                <DeliverySlotVendorPanel order={order} companyId={companyId!} onRefresh={refetch} />
              </motion.div>
            )}

            {/* Status timeline */}
            <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
              <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-4">Status</p>
              <OrderStatusTimeline
                status={order.orderStatus}
                fulfillmentMethod={order.fulfillmentMethod}
                hasPickupReady={hasPickupReady}
              />
            </motion.div>

            {/* Pickup panel (PICKUP orders) */}
            {order.fulfillmentMethod === "PICKUP" && (
              <motion.div variants={fadeInUp}>
                <PickupPanel order={order} />
              </motion.div>
            )}

            {/* Shipping info (DELIVERY) */}
            {order.fulfillmentMethod === "DELIVERY" && order.shipStreet && (
              <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
                <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-3">Ship To</p>
                <div className="flex items-start gap-2 text-sm text-white/70 space-y-0.5">
                  <MapPin className="h-4 w-4 text-sky-200 mt-0.5 shrink-0" />
                  <div>
                    {order.shipRecipientName && <p className="text-white font-medium">{order.shipRecipientName}</p>}
                    <p>{order.shipStreet}</p>
                    {order.shipStreet2 && <p>{order.shipStreet2}</p>}
                    <p>{order.shipCity}{order.shipState ? `, ${order.shipState}` : ""} {order.shipPostalCode}</p>
                    <p>{order.shipCountry}</p>
                  </div>
                </div>
              </motion.div>
            )}

            {/* Tracking info */}
            {order.trackingNumber && (
              <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
                <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">Tracking</p>
                <p className="text-sm text-white font-mono">{order.trackingNumber}</p>
                {order.carrier && <p className="text-xs text-white/50 mt-0.5">Carrier: {order.carrier}</p>}
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
                      <span className={`text-xs px-2 py-0.5 rounded-full ${
                        item.fulfillmentStatus === "DELIVERED" ? "bg-green-500/15 text-green-300" :
                        item.fulfillmentStatus === "PICKUP_READY" ? "bg-blue-500/15 text-blue-300" :
                        item.fulfillmentStatus === "SHIPPED" ? "bg-indigo-500/15 text-indigo-300" :
                        "bg-white/10 text-white/60"
                      }`}>
                        {item.fulfillmentStatus.replace(/_/g, " ")}
                      </span>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm text-white">×{item.quantity}</p>
                      <p className="text-xs text-white/50">{order.currency} {(item.unitPrice * item.quantity).toFixed(2)}</p>
                    </div>
                  </div>
                ))}
              </div>
            </motion.div>
          </motion.div>
        )}
        <SectionFade bottom />
      </div>
    </div>
  );
}
