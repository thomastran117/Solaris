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
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 space-y-3">
      <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">Pickup Location</p>
      {locationName && (
        <div className="flex items-start gap-2">
          <MapPin className="h-4 w-4 text-sky-200 mt-0.5 shrink-0" />
          <p className="text-sm text-white font-medium">{locationName}</p>
        </div>
      )}
      <div className="flex items-center gap-2">
        {isDelivered ? (
          <>
            <CheckCircle className="h-4 w-4 text-green-400" />
            <span className="text-sm text-green-400 font-medium">Collected</span>
          </>
        ) : isReady ? (
          <>
            <span className="relative flex h-3 w-3">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75" />
              <span className="relative inline-flex rounded-full h-3 w-3 bg-green-400" />
            </span>
            <span className="text-sm text-green-400 font-medium">Ready for pickup!</span>
          </>
        ) : (
          <>
            <Clock className="h-4 w-4 text-white/50" />
            <span className="text-sm text-white/70">Preparing your order</span>
          </>
        )}
      </div>
      {pickupReadyAt && (
        <p className="text-xs text-white/50">
          Ready since {new Date(pickupReadyAt).toLocaleString()}
        </p>
      )}
    </div>
  );
}
