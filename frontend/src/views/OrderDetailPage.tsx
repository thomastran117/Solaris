"use client";

import { useState } from "react";
import { useRouter, useParams, useSearchParams } from "next/navigation";
import { motion } from "framer-motion";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Package } from "lucide-react";
import OrderStatusTimeline from "../components/order/OrderStatusTimeline";
import OrderStatusHistoryTimeline from "../components/order/OrderStatusHistoryTimeline";
import TrackingPanel from "../components/order/TrackingPanel";
import TrackingTimeline from "../components/order/TrackingTimeline";
import PickupPanel from "../components/order/PickupPanel";
import DeliverySlotPanel from "../components/order/DeliverySlotPanel";
import ShippingRatePanel from "../components/order/ShippingRatePanel";
import { ConfirmDialog } from "../components/ui/ConfirmDialog";
import { PaymentPanel } from "../components/checkout/PaymentPanel";
import { ordersApi } from "../api/orders";
import { useOrderStream } from "../hooks/useOrderStream";
import { useAnims } from "../hooks/useAnims";
import DriverLocationIndicator from "../components/order/DriverLocationIndicator";

const CANCELLABLE = new Set(["RESERVED", "PAID", "PACKED"]);

export default function OrderDetailPage({
  stripePublishableKey = null,
}: {
  /** From the server; enables completing payment for a RESERVED order. */
  stripePublishableKey?: string | null;
}) {
  const { id } = useParams<{ id: string }>();
  const search = useSearchParams();
  const justPlaced =
    search.get("placed") === "1" || search.get("payment") === "return";
  const router = useRouter();
  const qc = useQueryClient();
  const { fadeInUp, stagger } = useAnims();
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const {
    data: order,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["order", id],
    queryFn: () => ordersApi.get(id!).then((r) => r.data),
    enabled: !!id,
  });

  const cancel = useMutation({
    mutationFn: () => ordersApi.cancel(id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["order", id] });
      router.push("/orders");
    },
    onError: () => {
      setCancelError("Failed to cancel order. Please try again.");
      cancel.reset();
    },
  });

  const { connected, driverLocation } = useOrderStream(id);

  const hasPickupReady =
    order?.items.some((i) => i.fulfillmentStatus === "PICKUP_READY") ?? false;

  return (
    <div className="text-foreground">
      <div className="max-w-3xl">
        <button
          onClick={() => router.push("/orders")}
          className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition mb-8"
        >
          <ArrowLeft className="h-4 w-4" /> Back to Orders
        </button>

        {justPlaced && order && order.status === "RESERVED" && (
          <div
            role="status"
            className="mb-6 rounded-xl border border-success/30 bg-success-subtle px-4 py-3 text-sm text-success"
          >
            Thanks! We&apos;re confirming your payment with the bank. This page
            updates on its own when your order is placed.
          </div>
        )}

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
          <p className="text-danger text-sm">
            Failed to load order. Please try again.
          </p>
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
                      #{order.id.slice(0, 8).toUpperCase()}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {new Date(order.createdAt).toLocaleString()}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  {connected && (
                    <div className="flex items-center justify-end gap-1.5 mb-1">
                      <span className="relative flex h-2 w-2">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75" />
                        <span className="relative inline-flex rounded-full h-2 w-2 bg-primary" />
                      </span>
                      <span className="text-xs text-link font-medium">
                        Live
                      </span>
                    </div>
                  )}
                  <p className="text-xl font-semibold text-foreground">
                    {order.currency} {order.totalAmount.toFixed(2)}
                  </p>
                  <p className="text-xs text-muted-foreground capitalize">
                    {order.status.toLowerCase().replace(/_/g, " ")}
                  </p>
                  {CANCELLABLE.has(order.status) && (
                    <button
                      onClick={() => {
                        setCancelError(null);
                        setShowCancelModal(true);
                      }}
                      className="mt-2 text-xs text-danger hover:text-danger transition underline underline-offset-2"
                    >
                      Cancel order
                    </button>
                  )}
                </div>
              </div>
            </motion.div>

            {/* Status timeline */}
            <motion.div
              variants={fadeInUp}
              className="rounded-xl border border-border bg-surface p-5"
            >
              <p className="text-xs font-semibold text-link mb-4">Status</p>
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
                  <TrackingPanel
                    trackingNumber={order.trackingNumber}
                    carrier={order.carrier}
                  />
                </motion.div>
                <motion.div
                  variants={fadeInUp}
                  className="rounded-xl border border-border bg-surface p-5"
                >
                  <p className="text-xs font-semibold text-link mb-4">
                    Tracking Updates
                  </p>
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
            {order.fulfillmentMethod === "DELIVERY" &&
              order.status === "RESERVED" && (
                <motion.div variants={fadeInUp}>
                  <ShippingRatePanel order={order} />
                </motion.div>
              )}
            {order.status === "RESERVED" && order.paymentClientSecret && (
              <motion.div variants={fadeInUp}>
                <PaymentPanel
                  order={order}
                  publishableKey={stripePublishableKey}
                  title="Complete payment"
                />
              </motion.div>
            )}

            {/* Shipping address */}
            {order.fulfillmentMethod === "DELIVERY" && order.shipStreet && (
              <motion.div
                variants={fadeInUp}
                className="rounded-xl border border-border bg-surface p-5"
              >
                <p className="text-xs font-semibold text-link mb-3">
                  Shipping To
                </p>
                <div className="text-sm text-muted-foreground space-y-0.5">
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
              <div className="border-t border-border mt-4 pt-4 flex justify-between">
                <span className="text-sm text-muted-foreground">Total</span>
                <span className="text-base font-semibold text-foreground">
                  {order.currency} {order.totalAmount.toFixed(2)}
                </span>
              </div>
            </motion.div>

            {/* History timeline */}
            <motion.div variants={fadeInUp}>
              <OrderStatusHistoryTimeline orderId={order.id} />
            </motion.div>
          </motion.div>
        )}
      </div>

      {/* Cancel confirmation modal */}
      <ConfirmDialog
        open={showCancelModal}
        onOpenChange={(open) => {
          setShowCancelModal(open);
          if (!open) setCancelError(null);
        }}
        title="Cancel this order?"
        description={
          <>
            This cancels your order and releases any reserved stock.
            {order &&
              ["RESERVED", "PAID", "PACKED"].includes(order.status) &&
              " If payment was already captured, a full refund goes to your original payment method."}
            {cancelError && (
              <span role="alert" className="mt-2 block font-medium text-danger">
                {cancelError}
              </span>
            )}
          </>
        }
        cancelLabel="Keep order"
        confirmLabel="Cancel order"
        destructive
        loading={cancel.isPending}
        onConfirm={() => cancel.mutate()}
      />
    </div>
  );
}
