import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { CalendarClock, Eye, EyeOff, Trash2 } from "lucide-react";
import {
  adminBundlesApi,
  type AdminBundle,
  type AdminProductStatus,
  type BatchUpdateBundlesPayload,
} from "../../api/catalog";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const STATUS_FILTERS: ReadonlyArray<{ value: AdminProductStatus | "ALL"; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "DRAFT", label: "Drafts" },
  { value: "SCHEDULED", label: "Scheduled" },
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Inactive" },
  { value: "DISCONTINUED", label: "Discontinued" },
  { value: "ARCHIVED", label: "Archived" },
];

const BULK_STATUS_OPTIONS: ReadonlyArray<{ value: Exclude<AdminProductStatus, "SCHEDULED">; label: string }> = [
  { value: "DRAFT", label: "Draft" },
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Inactive" },
  { value: "DISCONTINUED", label: "Discontinued" },
  { value: "ARCHIVED", label: "Archived" },
];


function StatusBadge({ status }: { status: AdminProductStatus }) {
  const tone: Record<AdminProductStatus, string> = {
    DRAFT: "bg-white/10 border-white/15 text-white/70",
    ACTIVE: "bg-green-500/15 border-green-400/30 text-green-300",
    SCHEDULED: "bg-blue-500/15 border-blue-400/30 text-sky-200",
    INACTIVE: "bg-yellow-500/15 border-yellow-400/30 text-yellow-200",
    DISCONTINUED: "bg-red-500/10 border-red-400/20 text-red-300/80",
    ARCHIVED: "bg-white/[0.04] border-white/10 text-white/45",
  };
  const label = status.charAt(0) + status.slice(1).toLowerCase();
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${tone[status]}`}>
      {status === "SCHEDULED" && <CalendarClock className="w-3 h-3" />}
      {label}
    </span>
  );
}

const inputCls = "rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-3 py-1.5 text-xs text-white placeholder:text-white/45 focus:outline-none focus:border-white/20";

export default function AdminBundlesPage() {
  const navigate = useNavigate();
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [statusFilter, setStatusFilter] = useState<AdminProductStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const [q, setQ] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedQ(q);
      setPage(0);
    }, 300);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [q]);

  useEffect(() => { setSelectedIds(new Set()); }, [page, statusFilter, debouncedQ]);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin-bundles", "list", { companyId, statusFilter, page, q: debouncedQ }],
    queryFn: () =>
      adminBundlesApi
        .list(companyId!, {
          status: statusFilter === "ALL" ? undefined : statusFilter,
          q: debouncedQ || undefined,
          page,
          size: 20,
        })
        .then(r => r.data),
    enabled: !!companyId,
  });

  const invalidateList = () => queryClient.invalidateQueries({ queryKey: ["admin-bundles", "list"] });
  const clearSelection = () => setSelectedIds(new Set());

  const removeMutation = useMutation({
    mutationFn: (id: string) => adminBundlesApi.remove(companyId!, id),
    onSuccess: invalidateList,
  });

  const batchDeleteMutation = useMutation({
    mutationFn: (ids: string[]) => adminBundlesApi.batchDelete(companyId!, ids),
    onSuccess: () => { invalidateList(); clearSelection(); },
  });

  const batchUpdateMutation = useMutation({
    mutationFn: (payload: BatchUpdateBundlesPayload) => adminBundlesApi.batchUpdate(companyId!, payload),
    onSuccess: () => { invalidateList(); clearSelection(); },
  });

  function handleDelete(b: AdminBundle) {
    if (!confirm(`Delete "${b.name}"? This cannot be undone.`)) return;
    removeMutation.mutate(b.id);
  }

  function handleBulkDelete() {
    if (!confirm(`Delete ${selectedIds.size} bundle(s)? This cannot be undone.`)) return;
    batchDeleteMutation.mutate([...selectedIds]);
  }

  function handleBulkStatus(status: Exclude<AdminProductStatus, "SCHEDULED">) {
    batchUpdateMutation.mutate({ ids: [...selectedIds], status });
  }

  const currentPageIds = data?.items.map(b => b.id) ?? [];
  const allSelected = currentPageIds.length > 0 && currentPageIds.every(id => selectedIds.has(id));
  const someSelected = currentPageIds.some(id => selectedIds.has(id));

  function toggleAll() {
    if (allSelected) {
      setSelectedIds(prev => {
        const next = new Set(prev);
        currentPageIds.forEach(id => next.delete(id));
        return next;
      });
    } else {
      setSelectedIds(prev => new Set([...prev, ...currentPageIds]));
    }
  }

  function toggleOne(id: string) {
    setSelectedIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  if (!companyId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <p className="text-white/60 text-sm">Sign in with a vendor account to manage bundles.</p>
      </div>
    );
  }

  const bulkPending = batchUpdateMutation.isPending || batchDeleteMutation.isPending;

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
        <div className="absolute bottom-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-sky-400/8 blur-[100px]" />
      </div>

      <div className="relative z-10 max-w-6xl mx-auto px-4 sm:px-6 py-10">
        <motion.header
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="flex items-end justify-between gap-4 mb-6"
        >
          <div>
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">
              Admin · Catalogue
            </p>
            <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-white">Bundles</h1>
            <p className="text-sm text-white/60 mt-1">Grouped product sets sold together.</p>
          </div>
          <button
            type="button"
            onClick={() => navigate("/admin/products")}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-white/20 hover:bg-white/10 text-sm font-semibold text-white transition-colors"
          >
            Products
          </button>
        </motion.header>

        {/* Search */}
        <div className="mb-4">
          <input
            type="search"
            value={q}
            onChange={e => setQ(e.target.value)}
            placeholder="Search bundles…"
            className="w-full rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-4 py-2 text-sm text-white placeholder:text-white/45 focus:outline-none focus:border-white/20"
          />
        </div>

        {/* Status filter pills */}
        <div className="flex flex-wrap gap-2 mb-4">
          {STATUS_FILTERS.map(f => {
            const active = statusFilter === f.value;
            return (
              <button
                key={f.value}
                type="button"
                onClick={() => { setStatusFilter(f.value); setPage(0); }}
                className={[
                  "px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors",
                  active
                    ? "border-sky-400/50 bg-sky-400/15 text-sky-100"
                    : "border-white/10 bg-white/[0.04] text-white/65 hover:bg-white/10",
                ].join(" ")}
              >
                {f.label}
              </button>
            );
          })}
        </div>

        {/* Bulk action toolbar */}
        {selectedIds.size > 0 && (
          <div className="mb-4 rounded-xl border border-white/10 bg-white/[0.06] backdrop-blur px-4 py-2.5 flex flex-wrap items-center gap-3 text-sm">
            <span className="text-white/70">{selectedIds.size} selected</span>
            <span className="text-white/25">|</span>

            <select
              defaultValue=""
              onChange={e => {
                if (e.target.value) {
                  handleBulkStatus(e.target.value as Exclude<AdminProductStatus, "SCHEDULED">);
                  e.target.value = "";
                }
              }}
              disabled={bulkPending}
              className={`${inputCls} disabled:opacity-40`}
            >
              <option value="" disabled>Set status…</option>
              {BULK_STATUS_OPTIONS.map(o => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>

            <button
              type="button"
              title="List on storefront"
              onClick={() => batchUpdateMutation.mutate({ ids: [...selectedIds], listed: true })}
              disabled={bulkPending}
              className="p-1.5 rounded-lg text-white/60 hover:text-green-300 hover:bg-white/10 transition-colors disabled:opacity-40"
            >
              <Eye className="w-4 h-4" />
            </button>
            <button
              type="button"
              title="Unlist from storefront"
              onClick={() => batchUpdateMutation.mutate({ ids: [...selectedIds], listed: false })}
              disabled={bulkPending}
              className="p-1.5 rounded-lg text-white/60 hover:text-white/80 hover:bg-white/10 transition-colors disabled:opacity-40"
            >
              <EyeOff className="w-4 h-4" />
            </button>

            <span className="text-white/25">|</span>

            <button
              type="button"
              onClick={handleBulkDelete}
              disabled={bulkPending}
              className="p-1.5 rounded-lg text-white/60 hover:text-red-400 hover:bg-white/10 transition-colors disabled:opacity-40"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        )}

        <motion.div
          variants={stagger}
          initial="hidden"
          animate="visible"
          className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm overflow-hidden"
        >
          {isLoading ? (
            <div className="p-6 space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="h-12 rounded-xl bg-white/[0.06] animate-pulse" />
              ))}
            </div>
          ) : isError ? (
            <p className="p-6 text-red-400 text-sm">Failed to load bundles.</p>
          ) : !data || data.items.length === 0 ? (
            <p className="p-10 text-center text-white/55 text-sm">No bundles yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase tracking-[0.18em] font-semibold text-white/45 bg-white/[0.04]">
                <tr>
                  <th className="px-4 py-3 w-8">
                    <input
                      type="checkbox"
                      checked={allSelected}
                      ref={el => { if (el) el.indeterminate = someSelected && !allSelected; }}
                      onChange={toggleAll}
                      className="accent-sky-400 cursor-pointer"
                    />
                  </th>
                  <th className="px-5 py-3">Name</th>
                  <th className="px-5 py-3">Price</th>
                  <th className="px-5 py-3">Items</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map(b => (
                  <motion.tr
                    key={b.id}
                    variants={fadeInUp}
                    className="border-t border-white/5 hover:bg-white/[0.04] transition-colors"
                  >
                    <td className="px-4 py-3">
                      <input
                        type="checkbox"
                        checked={selectedIds.has(b.id)}
                        onChange={() => toggleOne(b.id)}
                        className="accent-sky-400 cursor-pointer"
                      />
                    </td>
                    <td className="px-5 py-3 font-medium text-white">{b.name}</td>
                    <td className="px-5 py-3 text-white/80">
                      {b.currency} {b.price.toFixed(2)}
                    </td>
                    <td className="px-5 py-3 text-white/60">{b.items.length}</td>
                    <td className="px-5 py-3">
                      <StatusBadge status={b.status} />
                    </td>
                    <td className="px-5 py-3 text-right">
                      <button
                        type="button"
                        onClick={() => handleDelete(b)}
                        disabled={removeMutation.isPending}
                        aria-label={`Delete ${b.name}`}
                        className="p-2 rounded-lg text-white/60 hover:text-red-400 hover:bg-white/10 transition-colors disabled:opacity-40"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </motion.tr>
                ))}
              </tbody>
            </table>
          )}
        </motion.div>

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between mt-4 text-xs text-white/55">
            <span>
              Page {data.page + 1} of {data.totalPages} · {data.totalElements} total
            </span>
            <div className="inline-flex gap-2">
              <button
                type="button"
                disabled={!data.hasPrevious}
                onClick={() => setPage(p => Math.max(0, p - 1))}
                className="px-3 py-1.5 rounded-full border border-white/15 hover:bg-white/10 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                type="button"
                disabled={!data.hasNext}
                onClick={() => setPage(p => p + 1)}
                className="px-3 py-1.5 rounded-full border border-white/15 hover:bg-white/10 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
