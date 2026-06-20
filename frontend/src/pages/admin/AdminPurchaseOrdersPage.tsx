import { useState } from "react";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSearchParams, useNavigate } from "react-router-dom";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import {
  Plus,
  X,
  Truck,
  Send,
  CheckCircle,
  XCircle,
  Package,
  ChevronLeft,
  ChevronRight,
  Trash2,
} from "lucide-react";
import {
  listSuppliers,
  createSupplier,
  deleteSupplier,
  listPOs,
  createPO,
  sendPO,
  confirmPO,
  receiveItems,
  cancelPO,
} from "../../api/purchaseOrders";
import {
  createPOSchema,
  createSupplierSchema,
  receiveItemsSchema,
  type CreatePOFormValues,
  type CreateSupplierFormValues,
  type ReceiveItemsFormValues,
} from "../../schemas/purchaseOrder";
import type { PurchaseOrder, POStatus, Supplier, PurchaseOrderItem } from "../../types/purchaseOrders";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const STATUS_COLORS: Record<POStatus, string> = {
  DRAFT: "bg-white/10 text-white/60 border-white/10",
  SENT: "bg-blue-500/15 text-blue-400 border-blue-500/20",
  CONFIRMED: "bg-purple-500/15 text-purple-400 border-purple-500/20",
  PARTIALLY_RECEIVED: "bg-yellow-500/15 text-yellow-400 border-yellow-500/20",
  RECEIVED: "bg-green-500/15 text-green-400 border-green-500/20",
  CANCELLED: "bg-red-500/15 text-red-400 border-red-500/20",
};

const STATUS_LABELS: Record<POStatus, string> = {
  DRAFT: "Draft",
  SENT: "Sent",
  CONFIRMED: "Confirmed",
  PARTIALLY_RECEIVED: "Partial",
  RECEIVED: "Received",
  CANCELLED: "Cancelled",
};

const ALL_STATUSES: (POStatus | "ALL")[] = [
  "ALL", "DRAFT", "SENT", "CONFIRMED", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED",
];

const inputCls =
  "w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/25";

const labelCls = "block text-xs font-semibold text-white/60 mb-1 uppercase tracking-widest";

export default function AdminPurchaseOrdersPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { fadeInUp, stagger, hoverLift } = useAnims();

  const [tab, setTab] = useState<"pos" | "suppliers">("pos");
  const [statusFilter, setStatusFilter] = useState<POStatus | undefined>(undefined);
  const [page, setPage] = useState(0);

  const [showCreatePO, setShowCreatePO] = useState(false);
  const [showCreateSupplier, setShowCreateSupplier] = useState(false);
  const [receivingPO, setReceivingPO] = useState<PurchaseOrder | null>(null);

  const prefillProductId = searchParams.get("prefill") ?? "";

  // ── Queries ──────────────────────────────────────────────────────────────────

  const { data: poPage, isLoading: posLoading } = useQuery({
    queryKey: ["pos", companyId, statusFilter, page],
    queryFn: () => listPOs(companyId!, statusFilter, page),
    enabled: !!companyId,
  });
  const pos = poPage?.items ?? [];

  const { data: supplierPage, isLoading: suppliersLoading } = useQuery({
    queryKey: ["suppliers", companyId],
    queryFn: () => listSuppliers(companyId!),
    enabled: !!companyId,
  });
  const suppliers: Supplier[] = supplierPage?.items ?? [];

  // ── Mutations ─────────────────────────────────────────────────────────────────

  const invalidatePOs = () =>
    queryClient.invalidateQueries({ queryKey: ["pos", companyId] });

  const createPOMutation = useMutation({
    mutationFn: (values: CreatePOFormValues) =>
      createPO(companyId!, {
        ...values,
        restockRequestId: values.restockRequestId || undefined,
        expectedArrivalDate: values.expectedArrivalDate || undefined,
      }),
    onSuccess: () => {
      invalidatePOs();
      setShowCreatePO(false);
      poForm.reset();
    },
  });

  const sendPOMutation = useMutation({
    mutationFn: (poId: string) => sendPO(companyId!, poId),
    onSuccess: invalidatePOs,
  });

  const confirmPOMutation = useMutation({
    mutationFn: (poId: string) => confirmPO(companyId!, poId),
    onSuccess: invalidatePOs,
  });

  const receiveItemsMutation = useMutation({
    mutationFn: ({ poId, values }: { poId: string; values: ReceiveItemsFormValues }) =>
      receiveItems(companyId!, poId, values),
    onSuccess: () => {
      invalidatePOs();
      setReceivingPO(null);
    },
  });

  const cancelPOMutation = useMutation({
    mutationFn: (poId: string) => cancelPO(companyId!, poId),
    onSuccess: invalidatePOs,
  });

  const createSupplierMutation = useMutation({
    mutationFn: (values: CreateSupplierFormValues) =>
      createSupplier(companyId!, {
        ...values,
        email: values.email || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["suppliers", companyId] });
      setShowCreateSupplier(false);
      supplierForm.reset();
    },
  });

  const deleteSupplierMutation = useMutation({
    mutationFn: (supplierId: string) => deleteSupplier(companyId!, supplierId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["suppliers", companyId] }),
  });

  // ── Forms ─────────────────────────────────────────────────────────────────────

  const poForm = useForm<CreatePOFormValues>({
    resolver: zodResolver(createPOSchema),
    defaultValues: {
      supplierId: "",
      items: [{ productId: prefillProductId, orderedQty: 1, unitCostCents: 0 }],
    },
  });

  const { fields, append, remove } = useFieldArray({
    control: poForm.control,
    name: "items",
  });

  const supplierForm = useForm<CreateSupplierFormValues>({
    resolver: zodResolver(createSupplierSchema),
  });

  const receiveForm = useForm<ReceiveItemsFormValues>({
    resolver: zodResolver(receiveItemsSchema),
    defaultValues: { items: [] },
  });

  const openReceiveModal = (po: PurchaseOrder) => {
    setReceivingPO(po);
    receiveForm.reset({
      items: po.items
        .filter((i) => i.receivedQty < i.orderedQty)
        .map((i) => ({ itemId: i.id, receivedQty: i.orderedQty - i.receivedQty })),
    });
  };

  const formatCents = (cents: number) =>
    `$${(cents / 100).toLocaleString("en-US", { minimumFractionDigits: 2 })}`;

  return (
    <div className="min-h-screen bg-slate-950 px-6 py-10">
      <div className="max-w-5xl mx-auto">

        {/* Header */}
        <div className="mb-8">
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-1">
            Admin
          </p>
          <h1 className="text-3xl md:text-4xl font-extrabold text-white">
            Purchase Orders
          </h1>
          <p className="text-white/60 text-sm mt-1">
            Manage suppliers and vendor purchase orders.
          </p>
        </div>

        {/* Tabs */}
        <div className="flex gap-2 mb-6">
          {(["pos", "suppliers"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`px-4 py-2 rounded-full text-sm font-semibold transition border ${
                tab === t
                  ? "bg-blue-600 border-blue-500 text-white"
                  : "border-white/10 text-white/60 hover:bg-white/10"
              }`}
            >
              {t === "pos" ? "Purchase Orders" : "Suppliers"}
            </button>
          ))}
        </div>

        {/* ── POs Tab ──────────────────────────────────────────────────────────── */}
        {tab === "pos" && (
          <>
            <div className="flex items-center justify-between mb-5 gap-4 flex-wrap">
              {/* Status filter pills */}
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
                onClick={() => setShowCreatePO(true)}
                className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-4 py-2 rounded-full transition"
              >
                <Plus className="w-4 h-4" />
                New PO
              </button>
            </div>

            {posLoading ? (
              <div className="text-white/50 text-sm py-12 text-center">Loading purchase orders…</div>
            ) : pos.length === 0 ? (
              <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-12 text-center">
                <Truck className="w-10 h-10 text-sky-200 mx-auto mb-3 opacity-50" />
                <p className="text-white/60 text-sm">No purchase orders found.</p>
              </div>
            ) : (
              <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-4">
                {pos.map((po: PurchaseOrder) => (
                  <motion.div
                    key={po.id}
                    variants={fadeInUp}
                    whileHover={hoverLift}
                    className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm hover:shadow-md p-5"
                  >
                    <div className="flex items-start justify-between gap-4 flex-wrap">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap mb-1">
                          <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${STATUS_COLORS[po.status as POStatus]}`}>
                            {STATUS_LABELS[po.status as POStatus]}
                          </span>
                          <span className="text-xs text-white/40 font-mono">
                            #{po.id.substring(0, 8).toUpperCase()}
                          </span>
                        </div>
                        <p className="font-semibold text-white">{po.supplierName}</p>
                        <p className="text-xs text-white/50 mt-0.5">
                          {po.items.length} item{po.items.length !== 1 ? "s" : ""} ·{" "}
                          {formatCents(po.totalCostCents)} total
                          {po.expectedArrivalDate && ` · ETA ${po.expectedArrivalDate}`}
                        </p>
                      </div>

                      <div className="flex items-center gap-2 flex-shrink-0 flex-wrap">
                        {po.status === "DRAFT" && (
                          <button
                            onClick={() => sendPOMutation.mutate(po.id)}
                            disabled={sendPOMutation.isPending}
                            title="Send to supplier"
                            className="flex items-center gap-1 px-3 py-1.5 rounded-xl border border-blue-500/30 bg-blue-500/10 hover:bg-blue-500/20 text-blue-400 text-xs font-semibold transition disabled:opacity-50"
                          >
                            <Send className="w-3.5 h-3.5" />
                            Send
                          </button>
                        )}
                        {po.status === "SENT" && (
                          <button
                            onClick={() => confirmPOMutation.mutate(po.id)}
                            disabled={confirmPOMutation.isPending}
                            title="Confirm PO"
                            className="flex items-center gap-1 px-3 py-1.5 rounded-xl border border-purple-500/30 bg-purple-500/10 hover:bg-purple-500/20 text-purple-400 text-xs font-semibold transition disabled:opacity-50"
                          >
                            <CheckCircle className="w-3.5 h-3.5" />
                            Confirm
                          </button>
                        )}
                        {(po.status === "CONFIRMED" || po.status === "PARTIALLY_RECEIVED" || po.status === "SENT") && (
                          <button
                            onClick={() => openReceiveModal(po)}
                            title="Receive items"
                            className="flex items-center gap-1 px-3 py-1.5 rounded-xl border border-green-500/30 bg-green-500/10 hover:bg-green-500/20 text-green-400 text-xs font-semibold transition"
                          >
                            <Package className="w-3.5 h-3.5" />
                            Receive
                          </button>
                        )}
                        {po.status !== "RECEIVED" && po.status !== "CANCELLED" && (
                          <button
                            onClick={() => cancelPOMutation.mutate(po.id)}
                            disabled={cancelPOMutation.isPending}
                            title="Cancel PO"
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
            {poPage && poPage.totalPages > 1 && (
              <div className="flex items-center justify-between mt-6">
                <p className="text-xs text-white/50">
                  {poPage.totalElements} purchase orders
                </p>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setPage((p) => p - 1)}
                    disabled={!poPage.hasPrevious}
                    className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/60 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition"
                    aria-label="Previous page"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <span className="text-xs text-white/50">{page + 1} / {poPage.totalPages}</span>
                  <button
                    onClick={() => setPage((p) => p + 1)}
                    disabled={!poPage.hasNext}
                    className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/60 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition"
                    aria-label="Next page"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}

        {/* ── Suppliers Tab ─────────────────────────────────────────────────────── */}
        {tab === "suppliers" && (
          <>
            <div className="flex justify-end mb-5">
              <button
                onClick={() => setShowCreateSupplier(true)}
                className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-4 py-2 rounded-full transition"
              >
                <Plus className="w-4 h-4" />
                Add Supplier
              </button>
            </div>

            {suppliersLoading ? (
              <div className="text-white/50 text-sm py-12 text-center">Loading suppliers…</div>
            ) : suppliers.length === 0 ? (
              <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-12 text-center">
                <Truck className="w-10 h-10 text-sky-200 mx-auto mb-3 opacity-50" />
                <p className="text-white/60 text-sm">No suppliers yet.</p>
              </div>
            ) : (
              <div className="space-y-3">
                {suppliers.map((s: Supplier) => (
                  <div
                    key={s.id}
                    className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-4 flex items-center justify-between gap-4"
                  >
                    <div className="min-w-0">
                      <p className="font-semibold text-white">{s.name}</p>
                      {s.email && <p className="text-xs text-white/50">{s.email}</p>}
                      {s.phone && <p className="text-xs text-white/40">{s.phone}</p>}
                    </div>
                    <button
                      onClick={() => deleteSupplierMutation.mutate(s.id)}
                      disabled={deleteSupplierMutation.isPending}
                      className="p-2 rounded-xl border border-white/10 hover:bg-white/10 text-white/40 hover:text-red-400 transition"
                      title="Delete supplier"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {/* ── Create PO Modal ───────────────────────────────────────────────────── */}
      {showCreatePO && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="w-full max-w-lg rounded-3xl border border-white/10 bg-slate-900 p-8 shadow-2xl my-8">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-extrabold text-white">New Purchase Order</h2>
              <button
                onClick={() => { setShowCreatePO(false); poForm.reset(); }}
                className="text-white/40 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={poForm.handleSubmit((v) => createPOMutation.mutate(v))} className="space-y-4">
              <div>
                <label className={labelCls}>Supplier</label>
                <select {...poForm.register("supplierId")} className={inputCls}>
                  <option value="">Select a supplier</option>
                  {suppliers.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
                {poForm.formState.errors.supplierId && (
                  <p className="text-red-400 text-xs mt-1">{poForm.formState.errors.supplierId.message}</p>
                )}
              </div>

              <div>
                <label className={labelCls}>Expected Arrival Date</label>
                <input {...poForm.register("expectedArrivalDate")} type="date" className={inputCls} />
              </div>

              <div>
                <label className={labelCls}>Notes</label>
                <textarea {...poForm.register("notes")} rows={2} className={inputCls} />
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className={labelCls}>Line Items</label>
                  <button
                    type="button"
                    onClick={() => append({ productId: "", orderedQty: 1, unitCostCents: 0 })}
                    className="text-xs text-sky-200/80 hover:text-sky-200 flex items-center gap-1"
                  >
                    <Plus className="w-3 h-3" />
                    Add item
                  </button>
                </div>
                {fields.map((field, idx) => (
                  <div key={field.id} className="grid grid-cols-[1fr_auto_auto_auto] gap-2 mb-2 items-start">
                    <div>
                      <input
                        {...poForm.register(`items.${idx}.productId`)}
                        placeholder="Product ID (UUID)"
                        className={inputCls}
                      />
                      {poForm.formState.errors.items?.[idx]?.productId && (
                        <p className="text-red-400 text-xs mt-0.5">
                          {poForm.formState.errors.items[idx]?.productId?.message}
                        </p>
                      )}
                    </div>
                    <input
                      {...poForm.register(`items.${idx}.orderedQty`, { valueAsNumber: true })}
                      type="number"
                      min={1}
                      placeholder="Qty"
                      className="w-20 rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white focus:outline-none focus:border-white/25"
                    />
                    <input
                      {...poForm.register(`items.${idx}.unitCostCents`, { valueAsNumber: true })}
                      type="number"
                      min={0}
                      placeholder="Cost (¢)"
                      className="w-24 rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-2 text-sm text-white focus:outline-none focus:border-white/25"
                    />
                    {fields.length > 1 && (
                      <button
                        type="button"
                        onClick={() => remove(idx)}
                        className="p-2 text-white/40 hover:text-red-400 transition"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                ))}
                {poForm.formState.errors.items?.root && (
                  <p className="text-red-400 text-xs">{poForm.formState.errors.items.root.message}</p>
                )}
              </div>

              {createPOMutation.isError && (
                <p className="text-red-400 text-xs">Failed to create PO. Please try again.</p>
              )}

              <button
                type="submit"
                disabled={createPOMutation.isPending}
                className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-semibold py-2.5 rounded-full transition"
              >
                {createPOMutation.isPending ? "Creating…" : "Create Purchase Order"}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* ── Create Supplier Modal ─────────────────────────────────────────────── */}
      {showCreateSupplier && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-3xl border border-white/10 bg-slate-900 p-8 shadow-2xl">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-extrabold text-white">Add Supplier</h2>
              <button
                onClick={() => { setShowCreateSupplier(false); supplierForm.reset(); }}
                className="text-white/40 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form
              onSubmit={supplierForm.handleSubmit((v) => createSupplierMutation.mutate(v))}
              className="space-y-4"
            >
              <div>
                <label className={labelCls}>Name</label>
                <input {...supplierForm.register("name")} placeholder="Supplier name" className={inputCls} />
                {supplierForm.formState.errors.name && (
                  <p className="text-red-400 text-xs mt-1">{supplierForm.formState.errors.name.message}</p>
                )}
              </div>
              <div>
                <label className={labelCls}>Email</label>
                <input {...supplierForm.register("email")} type="email" placeholder="contact@supplier.com" className={inputCls} />
              </div>
              <div>
                <label className={labelCls}>Phone</label>
                <input {...supplierForm.register("phone")} placeholder="+1 555 000 0000" className={inputCls} />
              </div>
              <div>
                <label className={labelCls}>Address</label>
                <input {...supplierForm.register("address")} placeholder="123 Warehouse St" className={inputCls} />
              </div>

              {createSupplierMutation.isError && (
                <p className="text-red-400 text-xs">Failed to add supplier. Please try again.</p>
              )}

              <button
                type="submit"
                disabled={createSupplierMutation.isPending}
                className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-semibold py-2.5 rounded-full transition"
              >
                {createSupplierMutation.isPending ? "Saving…" : "Add Supplier"}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* ── Receive Items Modal ───────────────────────────────────────────────── */}
      {receivingPO && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-3xl border border-white/10 bg-slate-900 p-8 shadow-2xl">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-extrabold text-white">Receive Items</h2>
              <button
                onClick={() => setReceivingPO(null)}
                className="text-white/40 hover:text-white transition"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form
              onSubmit={receiveForm.handleSubmit((v) =>
                receiveItemsMutation.mutate({ poId: receivingPO.id, values: v })
              )}
              className="space-y-4"
            >
              {receivingPO.items
                .filter((i: PurchaseOrderItem) => i.receivedQty < i.orderedQty)
                .map((item: PurchaseOrderItem, idx: number) => (
                  <div key={item.id} className="rounded-xl border border-white/10 bg-white/[0.04] p-3">
                    <p className="text-sm font-semibold text-white mb-1">{item.productName}</p>
                    <p className="text-xs text-white/50 mb-2">
                      Ordered: {item.orderedQty} · Already received: {item.receivedQty} ·
                      Remaining: {item.orderedQty - item.receivedQty}
                    </p>
                    <input type="hidden" {...receiveForm.register(`items.${idx}.itemId`)} value={item.id} />
                    <div>
                      <label className={labelCls}>Qty to receive</label>
                      <input
                        {...receiveForm.register(`items.${idx}.receivedQty`, { valueAsNumber: true })}
                        type="number"
                        min={1}
                        max={item.orderedQty - item.receivedQty}
                        defaultValue={item.orderedQty - item.receivedQty}
                        className={inputCls}
                      />
                    </div>
                  </div>
                ))}

              {receiveItemsMutation.isError && (
                <p className="text-red-400 text-xs">Failed to receive items. Please try again.</p>
              )}

              <button
                type="submit"
                disabled={receiveItemsMutation.isPending}
                className="w-full bg-green-600 hover:bg-green-500 disabled:opacity-50 text-white font-semibold py-2.5 rounded-full transition"
              >
                {receiveItemsMutation.isPending ? "Saving…" : "Confirm Receipt"}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
