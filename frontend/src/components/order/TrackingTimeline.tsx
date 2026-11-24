import { useQuery } from "@tanstack/react-query";
import { MapPin, Loader2 } from "lucide-react";
import { ordersApi } from "../../api/orders";
import type { TrackingEvent } from "../../types/order";

interface Props {
  orderId: string;
}

export default function TrackingTimeline({ orderId }: Props) {
  const {
    data: events,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["tracking", orderId],
    queryFn: () => ordersApi.getTracking(orderId).then((r) => r.data),
    staleTime: 60_000,
    retry: false,
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-6">
        <Loader2 className="h-5 w-5 text-muted-foreground animate-spin" />
      </div>
    );
  }

  if (isError || !events || events.length === 0) {
    return (
      <p className="text-sm text-muted-foreground py-4">
        No tracking checkpoints available yet. Check back after the carrier
        scans your package.
      </p>
    );
  }

  return (
    <ol className="relative border-l border-border space-y-4 pl-5">
      {events.map((event: TrackingEvent, idx: number) => (
        <li key={idx} className="relative">
          <div className="absolute -left-[1.4rem] top-1 h-3 w-3 rounded-full border border-input bg-primary/10" />
          <p className="text-sm font-medium text-foreground">
            {event.status ?? "Update"}
          </p>
          {event.message && (
            <p className="text-xs text-muted-foreground mt-0.5">
              {event.message}
            </p>
          )}
          <div className="flex items-center gap-2 mt-1 text-xs text-muted-foreground">
            {event.location && (
              <span className="flex items-center gap-1">
                <MapPin className="h-3 w-3" />
                {event.location}
              </span>
            )}
            {event.occurredAt && (
              <span>{new Date(event.occurredAt).toLocaleString()}</span>
            )}
          </div>
        </li>
      ))}
    </ol>
  );
}
