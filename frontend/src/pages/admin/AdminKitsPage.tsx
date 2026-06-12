import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Plus, Pencil, Trash2, Layers } from "lucide-react";
import { kitsApi } from "../../api/kits";
import type { KitStatus } from "../../types/kit";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const STATUS_FILTERS: ReadonlyArray<{ value: KitStatus | "ALL"; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "DRAFT", label: "Drafts" },
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Inactive" },
  { value: "ARCHIVED", label: "Archived" },
];


function StatusBadge({ status }: { status: KitStatus }) {
  const tone: Record<KitStatus, string> = {
    DRAFT: "bg-white/10 border-white/15 text-white/70",
    ACTIVE: "bg-green-500/15 border-green-400/30 text-green-300",
    INACTIVE: "bg-yellow-500/15 border-yellow-400/30 text-yellow-200",
    ARCHIVED: "bg-white/[0.04] border-white/10 text-white/45",
  };
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${tone[status]}`}>
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  );
}

export default function AdminKitsPage() {
  const navigate = useNavigate();
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [statusFilter, setStatusFilter] = useState<KitStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin-kits", "list", { companyId, statusFilter, page }],
    queryFn: () =>
      kitsApi.list(companyId!, {
        status: statusFilter === "ALL" ? undefined : statusFilter,
        page,
        size: 20,
      }).then(r => r.data),
    enabled: !!companyId,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["admin-kits", "list"] });

  const removeMutation = useMutation({
    mutationFn: (kitId: string) => kitsApi.remove(companyId!, kitId),
    onSuccess: invalidate,
  });

  const kits = data?.items ?? [];

  return (
    <div className="min-h-screen bg-slate-950 pt-20 pb-16 px-4 md:px-6">
      <div className="max-w-5xl mx-auto">
        <motion.div
          initial="hidden"
          animate="visible"
          variants={fadeInUp}
          className="flex items-center justify-between mb-8"
        >
          <div>
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-1">Admin</p>
            <h1 className="text-3xl font-extrabold text-white">Product Kits</h1>
            <p className="text-sm text-white/60 mt-1">Configurable products where customers choose components.</p>
          </div>
          <button
            onClick={() => navigate("/admin/kits/new")}
            className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold px-4 py-2 rounded-full transition-colors"
          >
            <Plus className="w-4 h-4" />
            New Kit
          </button>
        </motion.div>

        {/* Status filter */}
        <div className="flex gap-2 mb-6 flex-wrap">
          {STATUS_FILTERS.map(f => (
            <button
              key={f.value}
              onClick={() => { setStatusFilter(f.value); setPage(0); }}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
                statusFilter === f.value
                  ? "bg-blue-500/20 border-blue-400/40 text-sky-200"
                  : "border-white/10 bg-white/[0.04] text-white/60 hover:bg-white/[0.08]"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        {isLoading && (
          <div className="flex justify-center py-16">
            <div className="w-8 h-8 rounded-full border-2 border-blue-500/40 border-t-blue-400 animate-spin" />
          </div>
        )}

        {isError && (
          <p className="text-red-400/80 text-sm text-center py-12">Failed to load kits.</p>
        )}

        {!isLoading && !isError && kits.length === 0 && (
          <div className="text-center py-20">
            <Layers className="w-10 h-10 text-white/20 mx-auto mb-3" />
            <p className="text-white/50 text-sm">No kits yet.</p>
            <p className="text-white/35 text-xs mt-1">Create your first configurable product kit.</p>
          </div>
        )}

        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="flex flex-col gap-3"
        >
          {kits.map(kit => (
            <motion.div
              key={kit.id}
              variants={fadeInUp}
              className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur px-5 py-4 flex items-center gap-4"
            >
              {kit.thumbnailUrl ? (
                <img
                  src={kit.thumbnailUrl}
                  alt={kit.name}
                  className="w-12 h-12 rounded-xl object-cover border border-white/10 shrink-0"
                />
              ) : (
                <div className="w-12 h-12 rounded-xl border border-white/10 bg-white/[0.04] flex items-center justify-center shrink-0">
                  <Layers className="w-5 h-5 text-white/30" />
                </div>
              )}

              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="text-sm font-semibold text-white truncate">{kit.name}</p>
                  <StatusBadge status={kit.status} />
                  {!kit.listed && (
                    <span className="text-xs text-white/40 italic">unlisted</span>
                  )}
                </div>
                <p className="text-xs text-white/50 mt-0.5">
                  {kit.slots.length} slot{kit.slots.length !== 1 ? "s" : ""} ·{" "}
                  {new Intl.NumberFormat("en-US", { style: "currency", currency: kit.currency }).format(kit.computedMinPrice)}
                  {kit.computedMinPrice !== kit.computedMaxPrice && (
                    <> – {new Intl.NumberFormat("en-US", { style: "currency", currency: kit.currency }).format(kit.computedMaxPrice)}</>
                  )}
                </p>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => navigate(`/admin/kits/${kit.id}/edit`)}
                  className="w-8 h-8 rounded-xl border border-white/10 bg-white/[0.04] flex items-center justify-center text-white/60 hover:text-white hover:bg-white/10 transition-colors"
                  title="Edit"
                >
                  <Pencil className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={() => {
                    if (confirm(`Delete "${kit.name}"?`)) removeMutation.mutate(kit.id);
                  }}
                  className="w-8 h-8 rounded-xl border border-white/10 bg-white/[0.04] flex items-center justify-center text-white/60 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                  title="Delete"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </motion.div>
          ))}
        </motion.div>

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 mt-8">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={!data.hasPrevious}
              className="px-4 py-1.5 rounded-full text-xs font-semibold border border-white/10 bg-white/[0.04] text-white/60 hover:bg-white/[0.08] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              Previous
            </button>
            <span className="text-xs text-white/45">
              Page {page + 1} of {data.totalPages}
            </span>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={!data.hasNext}
              className="px-4 py-1.5 rounded-full text-xs font-semibold border border-white/10 bg-white/[0.04] text-white/60 hover:bg-white/[0.08] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
