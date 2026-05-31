import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { store } from "../stores";
import Environment from "../configuration/Environment";

export function useDriverWebSocket(orderId: string | undefined): {
  connected: boolean;
  sendLocation: (lat: number, lng: number) => void;
  sendStatus: (status: "PICKED_UP" | "ARRIVED" | "DELIVERED") => void;
} {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!orderId) return;

    const token = store.getState().auth.accessToken;

    const client = new Client({
      brokerURL: `${Environment.backend_url_ws}/ws/delivery`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => setConnected(true),
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      reconnectDelay: 5000,
    });

    client.activate();
    clientRef.current = client;

    client.onConnect = () => {
      setConnected(true);
      client.subscribe("/user/queue/assignments", (msg) => {
        try {
          const payload = JSON.parse(msg.body);
          console.info("[Driver] Assignment received:", payload);
        } catch { /* ignore */ }
      });
    };

    return () => {
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [orderId]);

  const sendLocation = useCallback((lat: number, lng: number) => {
    const client = clientRef.current;
    if (!client?.connected || !orderId) return;
    client.publish({
      destination: `/app/delivery/${orderId}/location`,
      body: JSON.stringify({ lat, lng, timestamp: new Date().toISOString() }),
    });
  }, [orderId]);

  const sendStatus = useCallback((status: "PICKED_UP" | "ARRIVED" | "DELIVERED") => {
    const client = clientRef.current;
    if (!client?.connected || !orderId) return;
    client.publish({
      destination: `/app/delivery/${orderId}/status`,
      body: JSON.stringify({ status }),
    });
  }, [orderId]);

  return { connected, sendLocation, sendStatus };
}
