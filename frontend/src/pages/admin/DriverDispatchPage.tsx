import { useState } from "react";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Truck, UserCheck, UserX } from "lucide-react";
import NavyGridGlowBackground from "../../components/layout/NavyGridGlowBackground";
import SectionGlow from "../../components/section/SectionGlow";
import SectionFade from "../../components/section/SectionFade";
import SectionTitle from "../../components/section/SectionTitle";
import { companyOrdersApi } from "../../api/companyOrders";
import type { CompanyOrder } from "../../types/order";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";


interface AssignFormProps {
  companyId: string;
  orderId: string;
  currentDriverId: string | null;
  onDone: () => void;
}

function AssignForm({ companyId, orderId, currentDriverId, onDone }: AssignFormProps) {
  const qc = useQueryClient();
  const [driverId, setDriverId] = useState(currentDriverId ?? "");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (id: string) => companyOrdersApi.assignDriver(companyId, orderId, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["company-orders-shipped", companyId] });
      onDone();
    },
    onError: () => setError("Failed to assign driver. Check the driver ID and try again."),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmed = driverId.trim();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(trimmed)) {
      setError("Please enter a valid driver UUID.");
      return;
    }
    mutation.mutate(trimmed);
  };

  return (
    <form onSubmit={handleSubmit} className="mt-3 flex flex-col gap-2">
      <input
        type="text"
        value={driverId}
        onChange={(e) => setDriverId(e.target.value)}
        placeholder="Driver UUID"
        className="w-full rounded-lg border border-white/20 bg-white/[0.06] px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-blue-400"
        autoFocus
      />
      {error && <p className="text-xs text-red-400">{error}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={mutation.isPending}
          className="flex-1 rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-60 px-3 py-1.5 text-sm font-medium text-white transition"
        >
          {mutation.isPending ? "Assigning…" : "Assign"}
        </button>
        <button
          type="button"
          onClick={onDone}
          className="rounded-lg border border-white/20 px-3 py-1.5 text-sm text-white/70 hover:bg-white/10 transition"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}

function OrderDispatchCard({ order, companyId }: { order: CompanyOrder; companyId: string }) {
  const { fadeInUp } = useAnims();
  const [assigning, setAssigning] = useState(false);

  const destination =
    [order.shipRecipientName, order.shipCity, order.shipCountry].filter(Boolean).join(", ") || "—";

  return (
    <motion.div variants={fadeInUp} className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs text-white/40 font-mono truncate">{order.orderId}</p>
          <p className="text-sm text-white/80 mt-0.5 truncate">{destination}</p>
          {order.shippedAt && (
            <p className="text-xs text-white/40 mt-0.5">
              Shipped {new Date(order.shippedAt).toLocaleDateString()}
            </p>
          )}
        </div>

        <div className="shrink-0 flex flex-col items-end gap-2">
          {order.assignedDriverId ? (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-green-500/30 bg-green-500/10 px-2.5 py-1 text-xs font-medium text-green-400">
              <UserCheck size={12} />
              Assigned
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/[0.04] px-2.5 py-1 text-xs font-medium text-white/50">
              <UserX size={12} />
              Unassigned
            </span>
          )}

          {!assigning && (
            <button
              onClick={() => setAssigning(true)}
              className="text-xs text-sky-400 hover:text-sky-300 transition underline underline-offset-2"
            >
              {order.assignedDriverId ? "Reassign" : "Assign driver"}
            </button>
          )}
        </div>
      </div>

      {order.assignedDriverId && !assigning && (
        <p className="text-xs text-white/40 font-mono">Driver: {order.assignedDriverId}</p>
      )}

      {assigning && (
        <AssignForm
          companyId={companyId}
          orderId={order.orderId}
          currentDriverId={order.assignedDriverId}
          onDone={() => setAssigning(false)}
        />
      )}
    </motion.div>
  );
}

export default function DriverDispatchPage() {
  const { fadeInUp, stagger } = useAnims();
  const { companyId } = useSelector((state: RootState) => state.auth);
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["company-orders-shipped", companyId, page],
    queryFn: () =>
      companyOrdersApi.list(companyId!, { status: "SHIPPED", page, size: 20 }).then((r) => r.data),
    enabled: !!companyId,
  });

  const orders = data?.content ?? [];
  const totalPages = data?.totalPages ?? 1;

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />
      <div className="relative z-10 max-w-3xl mx-auto px-4 py-20">
        <SectionFade top />
        <SectionGlow variant="b" />

        <motion.div initial="hidden" animate="visible" variants={stagger} className="space-y-8">
          <motion.div variants={fadeInUp}>
            <SectionTitle
              kicker="Delivery"
              title="Driver Dispatch"
              subtitle="Assign drivers to shipped orders and track their status."
            />
          </motion.div>

          {isLoading ? (
            <motion.div variants={fadeInUp} className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-24 animate-pulse" />
              ))}
            </motion.div>
          ) : orders.length === 0 ? (
            <motion.div
              variants={fadeInUp}
              className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-10 flex flex-col items-center gap-3 text-white/50"
            >
              <Truck size={32} className="text-white/20" />
              <p className="text-sm">No shipped orders awaiting dispatch.</p>
            </motion.div>
          ) : (
            <motion.div variants={stagger} className="space-y-4">
              {orders.map((order) => (
                <OrderDispatchCard key={order.orderId} order={order} companyId={companyId!} />
              ))}
            </motion.div>
          )}

          {totalPages > 1 && (
            <motion.div variants={fadeInUp} className="flex justify-center gap-2">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="px-4 py-1.5 rounded-full text-sm border border-white/20 text-white/70 hover:bg-white/10 disabled:opacity-40 transition"
              >
                Previous
              </button>
              <span className="px-3 py-1.5 text-sm text-white/50">
                {page + 1} / {totalPages}
              </span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="px-4 py-1.5 rounded-full text-sm border border-white/20 text-white/70 hover:bg-white/10 disabled:opacity-40 transition"
              >
                Next
              </button>
            </motion.div>
          )}
        </motion.div>
      </div>
    </div>
  );
}
