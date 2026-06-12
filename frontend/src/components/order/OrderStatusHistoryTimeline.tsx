import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { ordersApi } from "../../api/orders";
import type { OrderStatusHistoryEntry } from "../../types/order";
import { useAnims } from "../../hooks/useAnims";


function formatStatus(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function formatEventType(raw: string): string {
  if (raw === "STATUS_CHANGED") return "Status update";
  return formatStatus(raw);
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

interface Props {
  orderId: string;
}

export default function OrderStatusHistoryTimeline({ orderId }: Props) {
  const { fadeInUp, stagger } = useAnims();

  const { data: entries } = useQuery<OrderStatusHistoryEntry[]>({
    queryKey: ["order-history", orderId],
    queryFn: () => ordersApi.getHistory(orderId).then((r) => r.data),
    staleTime: 30_000,
  });

  if (!entries || entries.length === 0) return null;

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
      <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-4">
        History
      </p>
      <motion.ul
        className="relative pl-4 border-l border-white/10 space-y-5"
        variants={stagger}
        initial="hidden"
        animate="visible"
      >
        {entries.map((entry) => (
          <motion.li key={entry.id} variants={fadeInUp} className="relative">
            <span className="absolute -left-[1.125rem] top-1 h-3 w-3 rounded-full bg-blue-500/30 border border-white/20" />
            <div className="flex flex-col gap-0.5">
              <span className="text-sm font-medium text-white">
                {entry.status ? formatStatus(entry.status) : formatEventType(entry.eventType)}
              </span>
              {entry.note && (
                <span className="text-xs text-white/60">{entry.note}</span>
              )}
              <span className="text-xs text-white/40">{formatTime(entry.occurredAt)}</span>
            </div>
          </motion.li>
        ))}
      </motion.ul>
    </div>
  );
}
