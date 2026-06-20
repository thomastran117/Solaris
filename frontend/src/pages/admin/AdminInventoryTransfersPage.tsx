import { useState } from "react";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import {
  Plus,
  X,
  Send,
  PackageCheck,
  XCircle,
  ArrowLeftRight,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import {
  listTransfers,
  createTransfer,
  dispatchTransfer,
  receiveTransfer,
  cancelTransfer,
  listInventoryLocations,
} from "../../api/inventoryTransfers";
import {
  createTransferSchema,
  type CreateTransferFormValues,
} from "../../schemas/inventoryTransfer";
import type { InventoryTransfer, TransferStatus } from "../../types/inventoryTransfers";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

/** Pulls the most specific message out of the backend ApiResponse error envelope. */
function apiErrorMessage(error: unknown, fallback: string): string {
  const e = error as {
    apiMessage?: string;
    response?: {
      data?: { message?: string; error?: { details?: { detail?: string } } };
    };
  };
  return (
    e?.response?.data?.error?.details?.detail ??
    e?.apiMessage ??
    e?.response?.data?.message ??
    fallback
  );
}

const STATUS_COLORS: Record<TransferStatus, string> = {
  PENDING: "bg-white/10 text-white/60 border-white/10",
  IN_TRANSIT: "bg-blue-500/15 text-blue-400 border-blue-500/20",
  RECEIVED: "bg-green-500/15 text-green-400 border-green-500/20",
  CANCELLED: "bg-red-500/15 text-red-400 border-red-500/20",
};

const STATUS_LABELS: Record<TransferStatus, string> = {
  PENDING: "Pending",
  IN_TRANSIT: "In Transit",
  RECEIVED: "Received",
  CANCELLED: "Cancelled",
};

const ALL_STATUSES: (TransferStatus | "ALL")[] = [
  "ALL", "PENDING", "IN_TRANSIT", "RECEIVED", "CANCELLED",
];

const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25";

const labelCls = "block text-xs font-semibold text-white/60 mb-1 uppercase tracking-widest";

export default function AdminInventoryTransfersPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger, hoverLift } = useAnims();

  const [statusFilter, setStatusFilter] = useState<TransferStatus | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);

  // ── Queries ──────────────────────────────────────────────────────────────────

  const { data: transferPage, isLoading } = useQuery({
    queryKey: ["transfers", companyId, statusFilter, page],
    queryFn: () => listTransfers(companyId!, statusFilter, undefined, page),
    enabled: !!companyId,
  });
  const transfers = transferPage?.items ?? [];

  const { data: locations = [] } = useQuery({
    queryKey: ["inventory-locations", companyId],
    queryFn: () => listInventoryLocations(companyId!),
    enabled: !!companyId,
  });

  // ── Mutations ─────────────────────────────────────────────────────────────────

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ["transfers", companyId] });

  /** Shared handler for status-transition actions: refresh and surface conflicts gracefully. */
  const onActionError = (error: unknown) => {
    invalidate();
    window.alert(apiErrorMessage(error, "This transfer was already updated. Refreshing."));
  };

  const createMutation = useMutation({
    mutationFn: (values: CreateTransferFormValues) =>
      createTransfer(companyId!, { ...values, notes: values.notes || undefined }),
    onSuccess: () => {
      invalidate();
      setShowCreate(false);
      form.reset();
    },
  });

  const dispatchMutation = useMutation({
    mutationFn: (transferId: string) => dispatchTransfer(companyId!, transferId),
    onSuccess: invalidate,
    onError: onActionError,
  });

  const receiveMutation = useMutation({
    mutationFn: (transferId: string) => receiveTransfer(companyId!, transferId),
    onSuccess: invalidate,
    onError: onActionError,
  });

  const cancelMutation = useMutation({
    mutationFn: (transferId: string) => cancelTransfer(companyId!, transferId),
    onSuccess: invalidate,
    onError: onActionError,
  });

  // ── Form ───────────────────────────────────────────────────────────────────────

  const form = useForm<CreateTransferFormValues>({
    resolver: zodResolver(createTransferSchema),
    defaultValues: { productId: "", fromLocationId: "", toLocationId: "", quantity: 1 },
  });

  return (
    <div className="min-h-screen bg-slate-950 px-6 py-10">
      <div className="max-w-5xl mx-auto">

        {/* Header */}
        <div className="mb-8">
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-1">
            Admin
          </p>
          <h1 className="text-3xl md:text-4xl font-extrabold text-white">
            Inventory Transfers
          </h1>
          <p className="text-white/60 text-sm mt-1">
            Move stock between your warehouses and stores with a full audit trail.
          </p>
        </div>

        {/* Filters + create */}
        <div className="flex items-center justify-between mb-5 gap-4 flex-wrap">
          <div className="flex flex-wrap gap-2">
            {ALL_STATUSES.map((s) => (
              <button
                key={s}
                onClick={() => {
                  setStatusFilter(s === "ALL" ? undefined : s);
                  setPage(0);
                }}
                className={`text-xs px-3 py-1 rounded-full border transition font-semibold ${
                  (s === "ALL" && !statusFilter) || s === statusFilter
                    ? "bg-blue-600 border-blue-500 text-white"
                    : "border-white/10 text-white/50 hover:bg-white/10"
                }`}
              >
                {s === "ALL" ? "All" : STATUS_LABELS[s]}
              </button>
            ))}
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-4 py-2 rounded-full transition"
          >
            <Plus className="w-4 h-4" />
            New Transfer
          </button>
        </div>

        {/* List */}
        {isLoading ? (
          <div className="text-white/50 text-sm py-12 text-center">Loading transfers…</div>
        ) : transfers.length === 0 ? (
          <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-12 text-center">
            <ArrowLeftRight className="w-10 h-10 text-sky-200 mx-auto mb-3 opacity-50" />
            <p className="text-white/60 text-sm">No transfers found.</p>
          </div>
        ) : (
          <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-4">
            {transfers.map((t: InventoryTransfer) => (
              <motion.div
                key={t.id}
                variants={fadeInUp}
                whileHover={hoverLift}
                className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md p-5"
              >
                <div className="flex items-start justify-between gap-4 flex-wrap">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap mb-1">
                      <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${STATUS_COLORS[t.status]}`}>
                        {STATUS_LABELS[t.status]}
                      </span>
                      <span className="text-xs text-white/40 font-mono">
                        #{t.id.substring(0, 8).toUpperCase()}
                      </span>
                    </div>
                    <p className="font-semibold text-white">{t.productName}</p>
                    <p className="text-xs text-white/50 mt-0.5 flex items-center gap-1.5">
                      {t.fromLocationName}
                      <ArrowLeftRight className="w-3 h-3 text-sky-200/70" />
                      {t.toLocationName} · <span className="text-white/70">{t.quantity} units</span>
                    </p>
                  </div>

                  <div className="flex items-center gap-2 flex-shrink-0 flex-wrap">
                    {t.status === "PENDING" && (
                      <button
                        onClick={() => dispatchMutation.mutate(t.id)}
                        disabled={dispatchMutation.isPending}
                        title="Mark as in transit"
                        className="flex items-center gap-1 px-3 py-1.5 rounded-xl border border-blue-500/30 bg-blue-500/10 hover:bg-blue-500/20 text-blue-400 text-xs font-semibold transition disabled:opacity-50"
                      >
                        <Send className="w-3.5 h-3.5" />
                        Dispatch
                      </button>
                    )}
                    {t.status === "IN_TRANSIT" && (
                      <button
                        onClick={() => receiveMutation.mutate(t.id)}
                        disabled={receiveMutation.isPending}
                        title="Receive transfer"
                        className="flex items-center gap-1 px-3 py-1.5 rounded-xl border border-green-500/30 bg-green-500/10 hover:bg-green-500/20 text-green-400 text-xs font-semibold transition disabled:opacity-50"
                      >
                        <PackageCheck className="w-3.5 h-3.5" />
                        Receive
                      </button>
                    )}
                    {t.status === "PENDING" && (
                      <button
                        onClick={() => {
                          if (window.confirm("Cancel this transfer?")) {
                            cancelMutation.mutate(t.id);
                          }
                        }}
                        disabled={cancelMutation.isPending}
                        title="Cancel transfer"
                        className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/40 hover:text-red-400 transition"
                      >
                        <XCircle className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              </motion.div>
            ))}
          </motion.div>
        )}

        {/* Pagination */}
        {transferPage && transferPage.totalPages > 1 && (
          <div className="flex items-center justify-between mt-6">
            <p className="text-xs text-white/50">{transferPage.totalElements} transfers</p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage((p) => p - 1)}
                disabled={!transferPage.hasPrevious}
                className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/60 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition"
                aria-label="Previous page"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="text-xs text-white/50">{page + 1} / {transferPage.totalPages}</span>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={!transferPage.hasNext}
                className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/60 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition"
                aria-label="Next page"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ── Create Transfer Modal ─────────────────────────────────────────────── */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="w-full max-w-lg rounded-3xl border border-white/10 bg-slate-900 p-8 shadow-2xl my-8">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-extrabold text-white">New Transfer</h2>
              <button
                onClick={() => { setShowCreate(false); form.reset(); }}
                className="text-white/40 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={form.handleSubmit((v) => createMutation.mutate(v))} className="space-y-4">
              <div>
                <label className={labelCls}>Product ID</label>
                <input
                  {...form.register("productId")}
                  placeholder="Product ID (UUID)"
                  className={inputCls}
                />
                {form.formState.errors.productId && (
                  <p className="text-red-400 text-xs mt-1">{form.formState.errors.productId.message}</p>
                )}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={labelCls}>From</label>
                  <select {...form.register("fromLocationId")} className={inputCls}>
                    <option value="">Source location</option>
                    {locations.map((l) => (
                      <option key={l.id} value={l.id}>{l.name} ({l.code})</option>
                    ))}
                  </select>
                  {form.formState.errors.fromLocationId && (
                    <p className="text-red-400 text-xs mt-1">{form.formState.errors.fromLocationId.message}</p>
                  )}
                </div>
                <div>
                  <label className={labelCls}>To</label>
                  <select {...form.register("toLocationId")} className={inputCls}>
                    <option value="">Destination location</option>
                    {locations.map((l) => (
                      <option key={l.id} value={l.id}>{l.name} ({l.code})</option>
                    ))}
                  </select>
                  {form.formState.errors.toLocationId && (
                    <p className="text-red-400 text-xs mt-1">{form.formState.errors.toLocationId.message}</p>
                  )}
                </div>
              </div>

              <div>
                <label className={labelCls}>Quantity</label>
                <input
                  {...form.register("quantity", { valueAsNumber: true })}
                  type="number"
                  min={1}
                  className={inputCls}
                />
                {form.formState.errors.quantity && (
                  <p className="text-red-400 text-xs mt-1">{form.formState.errors.quantity.message}</p>
                )}
              </div>

              <div>
                <label className={labelCls}>Notes</label>
                <textarea {...form.register("notes")} rows={2} className={inputCls} />
              </div>

              {createMutation.isError && (
                <p className="text-red-400 text-xs">
                  {apiErrorMessage(createMutation.error, "Failed to create transfer. Please try again.")}
                </p>
              )}

              <button
                type="submit"
                disabled={createMutation.isPending}
                className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-semibold py-2.5 rounded-full transition"
              >
                {createMutation.isPending ? "Creating…" : "Create Transfer"}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
