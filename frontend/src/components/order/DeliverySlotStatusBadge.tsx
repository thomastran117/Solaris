import type { DeliverySlotStatus } from "../../types/order";

const STATUS_STYLES: Record<DeliverySlotStatus, string> = {
  REQUESTED: "text-link bg-primary/10",
  CONFIRMED: "text-success bg-success-subtle",
  UNAVAILABLE: "text-danger bg-danger-subtle",
};

const STATUS_LABELS: Record<DeliverySlotStatus, string> = {
  REQUESTED: "Requested",
  CONFIRMED: "Confirmed",
  UNAVAILABLE: "Unavailable",
};

export default function DeliverySlotStatusBadge({
  status,
}: {
  status: DeliverySlotStatus;
}) {
  return (
    <span
      className={`text-xs font-medium px-2.5 py-1 rounded-full ${STATUS_STYLES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  );
}
