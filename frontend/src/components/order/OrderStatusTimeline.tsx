import { CheckCircle, Circle } from "lucide-react";
import type { OrderStatus, FulfillmentMethod } from "../../types/order";

const DELIVERY_STEPS: { key: OrderStatus | "start"; label: string }[] = [
  { key: "RESERVED", label: "Reserved" },
  { key: "PAID", label: "Paid" },
  { key: "PACKED", label: "Packed" },
  { key: "SHIPPED", label: "Shipped" },
  { key: "DELIVERED", label: "Delivered" },
];

const PICKUP_STEPS: { key: OrderStatus | "start"; label: string }[] = [
  { key: "RESERVED", label: "Reserved" },
  { key: "PAID", label: "Paid" },
  { key: "PACKED", label: "Packed" },
  { key: "PARTIALLY_FULFILLED", label: "Ready for Pickup" },
  { key: "DELIVERED", label: "Collected" },
];

const STATUS_ORDER: Record<OrderStatus, number> = {
  RESERVED: 0,
  UNDER_REVIEW: 0,
  FAILED: 0,
  CANCELLED: 0,
  PAID: 1,
  PACKED: 2,
  PARTIALLY_FULFILLED: 3,
  SHIPPED: 3,
  DELIVERED: 4,
  RETURNED: 4,
  REFUNDED: 4,
};

interface Props {
  status: OrderStatus;
  fulfillmentMethod: FulfillmentMethod;
  hasPickupReady?: boolean;
}

export default function OrderStatusTimeline({ status, fulfillmentMethod, hasPickupReady }: Props) {
  const steps = fulfillmentMethod === "PICKUP" ? PICKUP_STEPS : DELIVERY_STEPS;
  const currentOrder = STATUS_ORDER[status] ?? 0;

  const effectiveOrder =
    fulfillmentMethod === "PICKUP" && hasPickupReady && status === "PACKED"
      ? 3
      : currentOrder;

  return (
    <div className="flex items-center gap-0 w-full">
      {steps.map((step, idx) => {
        const done = idx < effectiveOrder || (idx === steps.length - 1 && status === "DELIVERED");
        const active = idx === effectiveOrder && !done;
        return (
          <div key={step.key} className="flex items-center flex-1 last:flex-none">
            <div className="flex flex-col items-center">
              {done ? (
                <CheckCircle className="h-5 w-5 text-green-400" />
              ) : active ? (
                <div className="h-5 w-5 rounded-full border-2 border-blue-400 bg-blue-400/20" />
              ) : (
                <Circle className="h-5 w-5 text-white/25" />
              )}
              <span className={`text-xs mt-1 text-center whitespace-nowrap ${done ? "text-green-400" : active ? "text-blue-300" : "text-white/40"}`}>
                {step.label}
              </span>
            </div>
            {idx < steps.length - 1 && (
              <div className={`flex-1 h-0.5 mx-1 mb-4 rounded ${idx < effectiveOrder ? "bg-green-400/50" : "bg-white/10"}`} />
            )}
          </div>
        );
      })}
    </div>
  );
}
