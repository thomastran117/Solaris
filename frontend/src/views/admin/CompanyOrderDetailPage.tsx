"use client";

import { useRouter, useParams } from "next/navigation";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Package, MapPin } from "lucide-react";
import OrderStatusTimeline from "../../components/order/OrderStatusTimeline";
import FulfillmentActionBar from "../../components/order/FulfillmentActionBar";
import DeliverySlotVendorPanel from "../../components/order/DeliverySlotVendorPanel";
import PickupPanel from "../../components/order/PickupPanel";
import { companyOrdersApi } from "../../api/companyOrders";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

export default function CompanyOrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const router = useRouter();
  const { fadeInUp, stagger } = useAnims();
  const { companyId } = useSelector((state: RootState) => state.auth);

  const {
    data: order,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["company-order", companyId, orderId],
    queryFn: () =>
      companyOrdersApi.get(companyId!, orderId!).then((r) => r.data),
    enabled: !!companyId && !!orderId,
  });

  const hasPickupReady =
    order?.items.some((i) => i.fulfillmentStatus === "PICKUP_READY") ?? false;

  return (
    <div className="text-foreground">
      <div className="max-w-3xl mx-auto px-4 py-10 sm:px-6">
        <button
          onClick={() => router.push("/admin/orders")}
          className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition mb-8"
        >
          <ArrowLeft className="h-4 w-4" /> Back to Orders
        </button>

        {isLoading && (
          <div className="space-y-4">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="rounded-xl border border-border bg-surface h-24 animate-pulse"
              />
            ))}
          </div>
        )}

        {isError && (
          <p className="text-danger text-sm">Failed to load order.</p>
        )}

        {order && (
          <motion.div
            initial="hidden"
            animate="visible"
            variants={stagger}
            className="space-y-6"
          >
            {/* Header */}
            <motion.div
              variants={fadeInUp}
              className="rounded-xl border border-border bg-surface p-5"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="bg-primary/10 border border-border rounded-xl p-2">
                    <Package className="h-5 w-5 text-link" />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wide">
                      Order
                    </p>
                    <p className="text-base font-mono font-bold text-foreground">
                      #{order.orderId.slice(0, 8).toUpperCase()}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {new Date(order.createdAt).toLocaleString()}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-xl font-semibold text-foreground">
                    {order.currency} {order.companyItemsTotal.toFixed(2)}
                  </p>
                  <p className="text-xs text-muted-foreground capitalize">
                    {order.orderStatus.toLowerCase().replace(/_/g, " ")}
                  </p>
                </div>
              </div>
            </motion.div>

            {/* Fulfillment action bar */}
            <motion.div variants={fadeInUp}>
              <FulfillmentActionBar
                order={order}
                companyId={companyId!}
                onRefresh={refetch}
              />
            </motion.div>

            {/* Delivery slot (DELIVERY orders with a requested slot) */}
            {order.fulfillmentMethod === "DELIVERY" &&
              order.deliverySlotStatus && (
                <motion.div variants={fadeInUp}>
                  <DeliverySlotVendorPanel
                    order={order}
                    companyId={companyId!}
                    onRefresh={refetch}
                  />
                </motion.div>
              )}

            {/* Status timeline */}
            <motion.div
              variants={fadeInUp}
              className="rounded-xl border border-border bg-surface p-5"
            >
              <p className="text-xs font-semibold text-link mb-4">Status</p>
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
              <motion.div
                variants={fadeInUp}
                className="rounded-xl border border-border bg-surface p-5"
              >
                <p className="text-xs font-semibold text-link mb-3">Ship To</p>
                <div className="flex items-start gap-2 text-sm text-muted-foreground space-y-0.5">
                  <MapPin className="h-4 w-4 text-link mt-0.5 shrink-0" />
                  <div>
                    {order.shipRecipientName && (
                      <p className="text-foreground font-medium">
                        {order.shipRecipientName}
                      </p>
                    )}
                    <p>{order.shipStreet}</p>
                    {order.shipStreet2 && <p>{order.shipStreet2}</p>}
                    <p>
                      {order.shipCity}
                      {order.shipState ? `, ${order.shipState}` : ""}{" "}
                      {order.shipPostalCode}
                    </p>
                    <p>{order.shipCountry}</p>
                  </div>
                </div>
              </motion.div>
            )}

            {/* Tracking info */}
            {order.trackingNumber && (
              <motion.div
                variants={fadeInUp}
                className="rounded-xl border border-border bg-surface p-5"
              >
                <p className="text-xs font-semibold text-link mb-2">Tracking</p>
                <p className="text-sm text-foreground font-mono">
                  {order.trackingNumber}
                </p>
                {order.carrier && (
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Carrier: {order.carrier}
                  </p>
                )}
              </motion.div>
            )}

            {/* Items */}
            <motion.div
              variants={fadeInUp}
              className="rounded-xl border border-border bg-surface p-5"
            >
              <p className="text-xs font-semibold text-link mb-4">Items</p>
              <div className="space-y-3">
                {order.items.map((item) => (
                  <div
                    key={item.id}
                    className="flex items-center justify-between gap-3"
                  >
                    <div>
                      <p className="text-sm font-medium text-foreground">
                        {item.productName}
                      </p>
                      {item.variantTitle && (
                        <p className="text-xs text-muted-foreground">
                          {item.variantTitle}
                        </p>
                      )}
                      <span
                        className={`text-xs px-2 py-0.5 rounded-full ${
                          item.fulfillmentStatus === "DELIVERED"
                            ? "bg-success-subtle text-success"
                            : item.fulfillmentStatus === "PICKUP_READY"
                              ? "bg-primary/10 text-link"
                              : item.fulfillmentStatus === "SHIPPED"
                                ? "bg-primary/10 text-link"
                                : "bg-muted text-muted-foreground"
                        }`}
                      >
                        {item.fulfillmentStatus.replace(/_/g, " ")}
                      </span>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm text-foreground">
                        ×{item.quantity}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {order.currency}{" "}
                        {(item.unitPrice * item.quantity).toFixed(2)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </motion.div>
          </motion.div>
        )}
      </div>
    </div>
  );
}
