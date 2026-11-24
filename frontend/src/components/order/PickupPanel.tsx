import { MapPin, Clock, CheckCircle } from "lucide-react";
import type { CompanyOrder, Order } from "../../types/order";

interface Props {
  order: Order | CompanyOrder;
}

function isCompanyOrder(o: Order | CompanyOrder): o is CompanyOrder {
  return "orderId" in o;
}

export default function PickupPanel({ order }: Props) {
  const status = isCompanyOrder(order) ? order.orderStatus : order.status;
  const locationName = order.pickupLocationName;
  const pickupReadyAt = order.pickupReadyAt;

  const hasPickupReady = isCompanyOrder(order)
    ? order.items.some((i) => i.fulfillmentStatus === "PICKUP_READY")
    : order.items.some((i) => i.fulfillmentStatus === "PICKUP_READY");

  const isDelivered = status === "DELIVERED";
  const isReady = hasPickupReady && !isDelivered;

  return (
    <div className="rounded-xl border border-border bg-surface p-5 space-y-3">
      <p className="text-xs font-semibold text-link">Pickup Location</p>
      {locationName && (
        <div className="flex items-start gap-2">
          <MapPin className="h-4 w-4 text-link mt-0.5 shrink-0" />
          <p className="text-sm text-foreground font-medium">{locationName}</p>
        </div>
      )}
      <div className="flex items-center gap-2">
        {isDelivered ? (
          <>
            <CheckCircle className="h-4 w-4 text-success" />
            <span className="text-sm text-success font-medium">Collected</span>
          </>
        ) : isReady ? (
          <>
            <span className="relative flex h-3 w-3">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-success-subtle opacity-75" />
              <span className="relative inline-flex rounded-full h-3 w-3 bg-success-subtle" />
            </span>
            <span className="text-sm text-success font-medium">
              Ready for pickup!
            </span>
          </>
        ) : (
          <>
            <Clock className="h-4 w-4 text-muted-foreground" />
            <span className="text-sm text-muted-foreground">
              Preparing your order
            </span>
          </>
        )}
      </div>
      {pickupReadyAt && (
        <p className="text-xs text-muted-foreground">
          Ready since {new Date(pickupReadyAt).toLocaleString()}
        </p>
      )}
    </div>
  );
}
