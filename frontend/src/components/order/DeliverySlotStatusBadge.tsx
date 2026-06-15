import type { DeliverySlotStatus } from "../../types/order";

const STATUS_STYLES: Record<DeliverySlotStatus, string> = {
  REQUESTED: "text-blue-300 bg-blue-500/15",
  CONFIRMED: "text-green-400 bg-green-500/15",
  UNAVAILABLE: "text-red-400 bg-red-500/15",
};

const STATUS_LABELS: Record<DeliverySlotStatus, string> = {
  REQUESTED: "Requested",
  CONFIRMED: "Confirmed",
  UNAVAILABLE: "Unavailable",
};

export default function DeliverySlotStatusBadge({ status }: { status: DeliverySlotStatus }) {
  return (
    <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${STATUS_STYLES[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  );
}
