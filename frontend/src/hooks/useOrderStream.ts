import { useEffect, useRef, useState } from "react";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import { useQueryClient } from "@tanstack/react-query";
import { store } from "../stores";
import Environment from "../configuration/Environment";
import type { DriverLocation, Order, SseStatusUpdateEvent } from "../types/order";

const TERMINAL_STATUSES = new Set(["DELIVERED", "CANCELLED", "REFUNDED", "FAILED"]);

export function useOrderStream(orderId: string | undefined): {
  connected: boolean;
  driverLocation: DriverLocation | null;
} {
  const qc = useQueryClient();
  const [connected, setConnected] = useState(false);
  const [driverLocation, setDriverLocation] = useState<DriverLocation | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!orderId) return;

    const ctrl = new AbortController();
    abortRef.current = ctrl;

    const token = store.getState().auth.accessToken;

    fetchEventSource(`${Environment.backend_url}/orders/${orderId}/stream`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: ctrl.signal,
      onopen: async (res) => {
        if (res.ok) {
          setConnected(true);
        } else if (res.status === 401 || res.status === 403) {
          // Token expired or revoked — abort immediately to avoid an infinite
          // reconnect loop. The user will need to re-authenticate.
          ctrl.abort();
        }
      },
      onmessage: (msg) => {
        if (msg.event === "heartbeat") return;

        if (msg.event === "snapshot") {
          try {
            const order: Order = JSON.parse(msg.data);
            qc.setQueryData(["order", orderId], order);
            if (TERMINAL_STATUSES.has(order.status)) ctrl.abort();
          } catch { /* ignore */ }
          return;
        }

        if (msg.event === "status_update") {
          try {
            const event: SseStatusUpdateEvent = JSON.parse(msg.data);
            qc.setQueryData(["order", orderId], (prev: Order | undefined) =>
              prev ? { ...prev, status: event.status } : prev
            );
            qc.invalidateQueries({ queryKey: ["order-history", orderId] });
            if (TERMINAL_STATUSES.has(event.status)) ctrl.abort();
          } catch { /* ignore */ }
          return;
        }

        if (msg.event === "driver_checkpoint") {
          qc.invalidateQueries({ queryKey: ["order-history", orderId] });
          return;
        }

        if (msg.event === "tracking_checkpoint") {
          qc.invalidateQueries({ queryKey: ["tracking", orderId] });
          return;
        }

        if (msg.event === "location_update") {
          try {
            const event: SseStatusUpdateEvent = JSON.parse(msg.data);
            if (event.driverLat != null && event.driverLng != null) {
              setDriverLocation({
                driverId: event.orderId,
                lat: event.driverLat,
                lng: event.driverLng,
                timestamp: event.occurredAt,
              });
            }
          } catch { /* ignore */ }
          return;
        }
      },
      onerror: (err) => {
        setConnected(false);
        // fetchEventSource passes the Response as the error when the server returns
        // a non-2xx status on open. Stop retrying on auth failures.
        if (err instanceof Response && (err.status === 401 || err.status === 403)) {
          ctrl.abort();
          return; // returning undefined would trigger a retry; void stops it
        }
        return undefined; // allow library to retry for transient errors
      },
      onclose: () => {
        setConnected(false);
      },
    });

    return () => {
      ctrl.abort();
      setConnected(false);
    };
  }, [orderId, qc]);

  return { connected, driverLocation };
}
