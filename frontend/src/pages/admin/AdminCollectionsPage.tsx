import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { Plus, Pencil, Trash2, Sparkles, ListTree, Layers } from "lucide-react";
import { adminCollectionsApi } from "../../api/merchandising";
import type {
  Collection,
  CollectionStatus,
  CollectionType,
} from "../../types/collection";
import type { RootState } from "../../stores";

const STATUS_FILTERS: ReadonlyArray<{ value: CollectionStatus | "ALL"; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "DRAFT", label: "Drafts" },
  { value: "ACTIVE", label: "Active" },
  { value: "ARCHIVED", label: "Archived" },
];

const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.55 } } };
  const stagger: Variants = prefersReducedMotion
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.04 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.06 } } };
  return { fadeInUp, stagger };
};

function TypeBadge({ type }: { type: CollectionType }) {
  const Icon = type === "DYNAMIC" ? Sparkles : ListTree;
  const tone =
    type === "DYNAMIC"
      ? "bg-blue-500/15 border-blue-400/30 text-sky-200"
      : "bg-white/10 border-white/15 text-white/70";
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${tone}`}>
      <Icon className="w-3 h-3" />
      {type === "DYNAMIC" ? "Dynamic" : "Static"}
    </span>
  );
}

function StatusBadge({ status }: { status: CollectionStatus }) {
  const tone = {
    DRAFT: "bg-white/10 border-white/15 text-white/70",
    ACTIVE: "bg-green-500/15 border-green-400/30 text-green-300",
    ARCHIVED: "bg-white/[0.04] border-white/10 text-white/45",
  }[status];
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${tone}`}>
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  );
}

export default function AdminCollectionsPage() {
  const navigate = useNavigate();
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [statusFilter, setStatusFilter] = useState<CollectionStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin-collections", "list", { companyId, statusFilter, page }],
    queryFn: () =>
      adminCollectionsApi
        .list(companyId!, {
          status: statusFilter === "ALL" ? undefined : statusFilter,
          page,
          size: 20,
        })
        .then(r => r.data),
    enabled: !!companyId,
  });

  const removeMutation = useMutation({
    mutationFn: (id: number) => adminCollectionsApi.remove(companyId!, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-collections", "list"] });
    },
  });

  function handleDelete(c: Collection) {
    if (!confirm(`Delete "${c.name}"? Products keep existing; only the grouping is removed.`)) return;
    removeMutation.mutate(c.id);
  }

  if (!companyId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <p className="text-white/60 text-sm">Sign in with a vendor account to manage collections.</p>
      </div>
    );
  }

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
              Admin · Merchandising
            </p>
            <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-white">Collections</h1>
            <p className="text-sm text-white/60 mt-1">
              Curate static groupings or auto-populate by tag, category, or brand.
            </p>
          </div>
          <button
            type="button"
            onClick={() => navigate("/admin/collections/new")}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-blue-600 hover:bg-blue-500 text-sm font-semibold text-white transition-colors"
          >
            <Plus className="w-4 h-4" />
            New collection
          </button>
        </motion.header>

        <div className="flex flex-wrap gap-2 mb-6">
          {STATUS_FILTERS.map(f => {
            const active = statusFilter === f.value;
            return (
              <button
                key={f.value}
                type="button"
                onClick={() => {
                  setStatusFilter(f.value);
                  setPage(0);
                }}
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
            <p className="p-6 text-red-400 text-sm">Failed to load collections.</p>
          ) : !data || data.items.length === 0 ? (
            <p className="p-10 text-center text-white/55 text-sm">No collections yet. Create one to start merchandising.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase tracking-[0.18em] font-semibold text-white/45 bg-white/[0.04]">
                <tr>
                  <th className="px-5 py-3">Name</th>
                  <th className="px-5 py-3">Type</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3">Products</th>
                  <th className="px-5 py-3">Featured</th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map(c => (
                  <motion.tr
                    key={c.id}
                    variants={fadeInUp}
                    className="border-t border-white/5 hover:bg-white/[0.04] transition-colors"
                  >
                    <td className="px-5 py-3">
                      <Link
                        to={`/admin/collections/${c.id}/products`}
                        className="font-medium text-white hover:text-sky-200"
                      >
                        {c.name}
                      </Link>
                      <p className="text-xs text-white/45 mt-0.5">/{c.slug}</p>
                    </td>
                    <td className="px-5 py-3">
                      <TypeBadge type={c.type} />
                    </td>
                    <td className="px-5 py-3">
                      <StatusBadge status={c.status} />
                    </td>
                    <td className="px-5 py-3 text-white/80">
                      <span className="inline-flex items-center gap-1.5">
                        <Layers className="w-3.5 h-3.5 text-white/50" />
                        {c.productCount}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-white/70">
                      {c.featured
                        ? `Yes${c.featuredRank != null ? ` · #${c.featuredRank}` : ""}`
                        : "—"}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <div className="inline-flex items-center gap-1">
                        <button
                          type="button"
                          onClick={() => navigate(`/admin/collections/${c.id}/edit`)}
                          aria-label={`Edit ${c.name}`}
                          className="p-2 rounded-lg text-white/60 hover:text-sky-200 hover:bg-white/10 transition-colors"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(c)}
                          disabled={removeMutation.isPending}
                          aria-label={`Delete ${c.name}`}
                          className="p-2 rounded-lg text-white/60 hover:text-red-400 hover:bg-white/10 transition-colors disabled:opacity-40"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
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
