"use client";

import { useState } from "react";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Truck, UserCheck, UserX } from "lucide-react";
import { SectionHeader } from "../../components/ui/SectionHeader";
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

function AssignForm({
  companyId,
  orderId,
  currentDriverId,
  onDone,
}: AssignFormProps) {
  const qc = useQueryClient();
  const [driverId, setDriverId] = useState(currentDriverId ?? "");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (id: string) =>
      companyOrdersApi.assignDriver(companyId, orderId, id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["company-orders-shipped", companyId] });
      onDone();
    },
    onError: () =>
      setError("Failed to assign driver. Check the driver ID and try again."),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmed = driverId.trim();
    if (
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
        trimmed
      )
    ) {
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
        className="w-full rounded-lg border border-input bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring"
        autoFocus
      />
      {error && <p className="text-xs text-danger">{error}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={mutation.isPending}
          className="flex-1 rounded-lg bg-primary hover:bg-primary-hover disabled:opacity-60 px-3 py-1.5 text-sm font-medium text-primary-foreground transition"
        >
          {mutation.isPending ? "Assigning…" : "Assign"}
        </button>
        <button
          type="button"
          onClick={onDone}
          className="rounded-lg border border-input px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted transition"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}

function OrderDispatchCard({
  order,
  companyId,
}: {
  order: CompanyOrder;
  companyId: string;
}) {
  const { fadeInUp } = useAnims();
  const [assigning, setAssigning] = useState(false);

  const destination =
    [order.shipRecipientName, order.shipCity, order.shipCountry]
      .filter(Boolean)
      .join(", ") || "—";

  return (
    <motion.div
      variants={fadeInUp}
      className="rounded-xl border border-border bg-surface p-5 space-y-3"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs text-muted-foreground font-mono truncate">
            {order.orderId}
          </p>
          <p className="text-sm text-foreground mt-0.5 truncate">
            {destination}
          </p>
          {order.shippedAt && (
            <p className="text-xs text-muted-foreground mt-0.5">
              Shipped {new Date(order.shippedAt).toLocaleDateString()}
            </p>
          )}
        </div>

        <div className="shrink-0 flex flex-col items-end gap-2">
          {order.assignedDriverId ? (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-success/40 bg-success-subtle px-2.5 py-1 text-xs font-medium text-success">
              <UserCheck size={12} />
              Assigned
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-2.5 py-1 text-xs font-medium text-muted-foreground">
              <UserX size={12} />
              Unassigned
            </span>
          )}

          {!assigning && (
            <button
              onClick={() => setAssigning(true)}
              className="text-xs text-link hover:text-link transition underline underline-offset-2"
            >
              {order.assignedDriverId ? "Reassign" : "Assign driver"}
            </button>
          )}
        </div>
      </div>

      {order.assignedDriverId && !assigning && (
        <p className="text-xs text-muted-foreground font-mono">
          Driver: {order.assignedDriverId}
        </p>
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
      companyOrdersApi
        .list(companyId!, { status: "SHIPPED", page, size: 20 })
        .then((r) => r.data),
    enabled: !!companyId,
  });

  const orders = data?.content ?? [];
  const totalPages = data?.totalPages ?? 1;

  return (
    <div className="text-foreground">
      <div className="max-w-4xl mx-auto px-4 py-10 sm:px-6">
        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="space-y-8"
        >
          <motion.div variants={fadeInUp}>
            <SectionHeader
              align="center"
              kicker="Delivery"
              title="Driver Dispatch"
              subtitle="Assign drivers to shipped orders and track their status."
            />
          </motion.div>

          {isLoading ? (
            <motion.div variants={fadeInUp} className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div
                  key={i}
                  className="rounded-xl border border-border bg-surface h-24 animate-pulse"
                />
              ))}
            </motion.div>
          ) : orders.length === 0 ? (
            <motion.div
              variants={fadeInUp}
              className="rounded-xl border border-border bg-surface p-10 flex flex-col items-center gap-3 text-muted-foreground"
            >
              <Truck size={32} className="text-muted-foreground" />
              <p className="text-sm">No shipped orders awaiting dispatch.</p>
            </motion.div>
          ) : (
            <motion.div variants={stagger} className="space-y-4">
              {orders.map((order) => (
                <OrderDispatchCard
                  key={order.orderId}
                  order={order}
                  companyId={companyId!}
                />
              ))}
            </motion.div>
          )}

          {totalPages > 1 && (
            <motion.div
              variants={fadeInUp}
              className="flex justify-center gap-2"
            >
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="px-4 py-1.5 rounded-full text-sm border border-input text-muted-foreground hover:bg-muted disabled:opacity-40 transition"
              >
                Previous
              </button>
              <span className="px-3 py-1.5 text-sm text-muted-foreground">
                {page + 1} / {totalPages}
              </span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="px-4 py-1.5 rounded-full text-sm border border-input text-muted-foreground hover:bg-muted disabled:opacity-40 transition"
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
