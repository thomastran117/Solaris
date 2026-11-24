"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Plus, Pencil, Trash2, Layers } from "lucide-react";
import { kitsApi } from "../../api/kits";
import type { KitStatus } from "../../types/kit";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const STATUS_FILTERS: ReadonlyArray<{
  value: KitStatus | "ALL";
  label: string;
}> = [
  { value: "ALL", label: "All" },
  { value: "DRAFT", label: "Drafts" },
  { value: "ACTIVE", label: "Active" },
  { value: "INACTIVE", label: "Inactive" },
  { value: "ARCHIVED", label: "Archived" },
];

function StatusBadge({ status }: { status: KitStatus }) {
  const tone: Record<KitStatus, string> = {
    DRAFT: "bg-muted border-border text-muted-foreground",
    ACTIVE: "bg-success-subtle border-success/40 text-success",
    INACTIVE: "bg-warning-subtle border-warning/40 text-warning",
    ARCHIVED: "bg-surface border-border text-muted-foreground",
  };
  return (
    <span
      className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${tone[status]}`}
    >
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  );
}

export default function AdminKitsPage() {
  const router = useRouter();
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [statusFilter, setStatusFilter] = useState<KitStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["admin-kits", "list", { companyId, statusFilter, page }],
    queryFn: () =>
      kitsApi
        .list(companyId!, {
          status: statusFilter === "ALL" ? undefined : statusFilter,
          page,
          size: 20,
        })
        .then((r) => r.data),
    enabled: !!companyId,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ["admin-kits", "list"] });

  const removeMutation = useMutation({
    mutationFn: (kitId: string) => kitsApi.remove(companyId!, kitId),
    onSuccess: invalidate,
  });

  const kits = data?.items ?? [];

  return (
    <div className="px-4 py-10 sm:px-6">
      <div className="max-w-5xl mx-auto">
        <motion.div
          initial="hidden"
          animate="visible"
          variants={fadeInUp}
          className="flex items-center justify-between mb-8"
        >
          <div>
            <p className="text-xs font-semibold text-link mb-1">Admin</p>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              Product Kits
            </h1>
            <p className="text-sm text-muted-foreground mt-1">
              Configurable products where customers choose components.
            </p>
          </div>
          <button
            onClick={() => router.push("/admin/kits/new")}
            className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-4 py-2 rounded-full transition-colors"
          >
            <Plus className="w-4 h-4" />
            New Kit
          </button>
        </motion.div>

        {/* Status filter */}
        <div className="flex gap-2 mb-6 flex-wrap">
          {STATUS_FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => {
                setStatusFilter(f.value);
                setPage(0);
              }}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
                statusFilter === f.value
                  ? "bg-primary/10 border-primary/40 text-link"
                  : "border-border bg-surface text-muted-foreground hover:bg-muted"
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        {isLoading && (
          <div className="flex justify-center py-16">
            <div className="w-8 h-8 rounded-full border-2 border-primary/40 border-t-primary animate-spin" />
          </div>
        )}

        {isError && (
          <p className="text-danger text-sm text-center py-12">
            Failed to load kits.
          </p>
        )}

        {!isLoading && !isError && kits.length === 0 && (
          <div className="text-center py-20">
            <Layers className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No kits yet.</p>
            <p className="text-muted-foreground text-xs mt-1">
              Create your first configurable product kit.
            </p>
          </div>
        )}

        <motion.div
          initial="hidden"
          animate="visible"
          variants={stagger}
          className="flex flex-col gap-3"
        >
          {kits.map((kit) => (
            <motion.div
              key={kit.id}
              variants={fadeInUp}
              className="rounded-xl border border-border bg-surface px-5 py-4 flex items-center gap-4"
            >
              {kit.thumbnailUrl ? (
                <img
                  src={kit.thumbnailUrl}
                  alt={kit.name}
                  className="w-12 h-12 rounded-xl object-cover border border-border shrink-0"
                />
              ) : (
                <div className="w-12 h-12 rounded-xl border border-border bg-surface flex items-center justify-center shrink-0">
                  <Layers className="w-5 h-5 text-muted-foreground" />
                </div>
              )}

              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="text-sm font-semibold text-foreground truncate">
                    {kit.name}
                  </p>
                  <StatusBadge status={kit.status} />
                  {!kit.listed && (
                    <span className="text-xs text-muted-foreground italic">
                      unlisted
                    </span>
                  )}
                </div>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {kit.slots.length} slot{kit.slots.length !== 1 ? "s" : ""} ·{" "}
                  {new Intl.NumberFormat("en-US", {
                    style: "currency",
                    currency: kit.currency,
                  }).format(kit.computedMinPrice)}
                  {kit.computedMinPrice !== kit.computedMaxPrice && (
                    <>
                      {" "}
                      –{" "}
                      {new Intl.NumberFormat("en-US", {
                        style: "currency",
                        currency: kit.currency,
                      }).format(kit.computedMaxPrice)}
                    </>
                  )}
                </p>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => router.push(`/admin/kits/${kit.id}/edit`)}
                  className="w-8 h-8 rounded-xl border border-border bg-surface flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                  title="Edit"
                >
                  <Pencil className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={() => {
                    if (confirm(`Delete "${kit.name}"?`))
                      removeMutation.mutate(kit.id);
                  }}
                  className="w-8 h-8 rounded-xl border border-border bg-surface flex items-center justify-center text-muted-foreground hover:text-danger hover:bg-danger-subtle transition-colors"
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
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={!data.hasPrevious}
              className="px-4 py-1.5 rounded-full text-xs font-semibold border border-border bg-surface text-muted-foreground hover:bg-muted disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              Previous
            </button>
            <span className="text-xs text-muted-foreground">
              Page {page + 1} of {data.totalPages}
            </span>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={!data.hasNext}
              className="px-4 py-1.5 rounded-full text-xs font-semibold border border-border bg-surface text-muted-foreground hover:bg-muted disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
