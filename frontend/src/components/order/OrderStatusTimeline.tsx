import { Check } from "lucide-react";
import type { OrderStatus, FulfillmentMethod } from "../../types/order";
import { cn } from "../../lib/cn";

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

type StepState = "complete" | "current" | "upcoming";

const STATE_LABEL: Record<StepState, string> = {
  complete: "completed",
  current: "current step",
  upcoming: "not started",
};

interface Props {
  status: OrderStatus;
  fulfillmentMethod: FulfillmentMethod;
  hasPickupReady?: boolean;
}

/** Order progress as an ordered list; the current step carries aria-current. */
export default function OrderStatusTimeline({
  status,
  fulfillmentMethod,
  hasPickupReady,
}: Props) {
  const steps = fulfillmentMethod === "PICKUP" ? PICKUP_STEPS : DELIVERY_STEPS;
  const currentOrder = STATUS_ORDER[status] ?? 0;

  const effectiveOrder =
    fulfillmentMethod === "PICKUP" && hasPickupReady && status === "PACKED"
      ? 3
      : currentOrder;

  return (
    <ol aria-label="Order progress" className="flex w-full items-start">
      {steps.map((step, idx) => {
        const done =
          idx < effectiveOrder ||
          (idx === steps.length - 1 && status === "DELIVERED");
        const state: StepState = done
          ? "complete"
          : idx === effectiveOrder
            ? "current"
            : "upcoming";
        return (
          <li
            key={step.key}
            data-state={state}
            aria-current={state === "current" ? "step" : undefined}
            className="flex flex-1 items-start last:flex-none"
          >
            <div className="flex flex-col items-center">
              <span
                aria-hidden
                className={cn(
                  "flex size-6 items-center justify-center rounded-full border-2",
                  state === "complete" &&
                    "border-success bg-success text-background",
                  state === "current" && "border-primary bg-primary/10",
                  state === "upcoming" && "border-border bg-surface"
                )}
              >
                {state === "complete" && <Check className="size-3.5" />}
                {state === "current" && (
                  <span className="size-2 rounded-full bg-primary" />
                )}
              </span>
              <span
                className={cn(
                  "mt-1.5 whitespace-nowrap text-center text-xs",
                  state === "upcoming"
                    ? "text-muted-foreground"
                    : "font-medium text-foreground"
                )}
              >
                {step.label}
                <span className="sr-only"> ({STATE_LABEL[state]})</span>
              </span>
            </div>
            {idx < steps.length - 1 && (
              <div
                aria-hidden
                className={cn(
                  "mx-1 mt-3 h-0.5 flex-1 rounded",
                  idx < effectiveOrder ? "bg-success" : "bg-border"
                )}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}
