"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Plus, Pencil, Trash2, Sparkles, ListTree, Layers } from "lucide-react";
import { adminCollectionsApi } from "../../api/merchandising";
import type {
  Collection,
  CollectionStatus,
  CollectionType,
} from "../../types/collection";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const STATUS_FILTERS: ReadonlyArray<{
  value: CollectionStatus | "ALL";
  label: string;
}> = [
  { value: "ALL", label: "All" },
  { value: "DRAFT", label: "Drafts" },
  { value: "ACTIVE", label: "Active" },
  { value: "ARCHIVED", label: "Archived" },
];

function TypeBadge({ type }: { type: CollectionType }) {
  const Icon = type === "DYNAMIC" ? Sparkles : ListTree;
  const tone =
    type === "DYNAMIC"
      ? "bg-primary/10 border-primary/40 text-link"
      : "bg-muted border-border text-muted-foreground";
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${tone}`}
    >
      <Icon className="w-3 h-3" />
      {type === "DYNAMIC" ? "Dynamic" : "Static"}
    </span>
  );
}

function StatusBadge({ status }: { status: CollectionStatus }) {
  const tone = {
    DRAFT: "bg-muted border-border text-muted-foreground",
    ACTIVE: "bg-success-subtle border-success/40 text-success",
    ARCHIVED: "bg-surface border-border text-muted-foreground",
  }[status];
  return (
    <span
      className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${tone}`}
    >
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  );
}

export default function AdminCollectionsPage() {
  const router = useRouter();
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [statusFilter, setStatusFilter] = useState<CollectionStatus | "ALL">(
    "ALL"
  );
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
        .then((r) => r.data),
    enabled: !!companyId,
  });

  const removeMutation = useMutation({
    mutationFn: (id: string) => adminCollectionsApi.remove(companyId!, id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["admin-collections", "list"],
      });
    },
  });

  function handleDelete(c: Collection) {
    if (
      !confirm(
        `Delete "${c.name}"? Products keep existing; only the grouping is removed.`
      )
    )
      return;
    removeMutation.mutate(c.id);
  }

  if (!companyId) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center px-6">
        <p className="text-muted-foreground text-sm">
          Sign in with a vendor account to manage collections.
        </p>
      </div>
    );
  }

  return (
    <div>
      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-10">
        <motion.header
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="flex items-end justify-between gap-4 mb-6"
        >
          <div>
            <p className="text-xs font-semibold text-link mb-2">
              Admin · Merchandising
            </p>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              Collections
            </h1>
            <p className="text-sm text-muted-foreground mt-1">
              Curate static groupings or auto-populate by tag, category, or
              brand.
            </p>
          </div>
          <button
            type="button"
            onClick={() => router.push("/admin/collections/new")}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-primary hover:bg-primary-hover text-sm font-semibold text-primary-foreground transition-colors"
          >
            <Plus className="w-4 h-4" />
            New collection
          </button>
        </motion.header>

        <div className="flex flex-wrap gap-2 mb-6">
          {STATUS_FILTERS.map((f) => {
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
                    ? "border-primary/40 bg-primary/10 text-link"
                    : "border-border bg-surface text-muted-foreground hover:bg-muted",
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
          className="rounded-xl border border-border bg-surface shadow-sm overflow-hidden"
        >
          {isLoading ? (
            <div className="p-6 space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <div
                  key={i}
                  className="h-12 rounded-xl bg-surface animate-pulse"
                />
              ))}
            </div>
          ) : isError ? (
            <p className="p-6 text-danger text-sm">
              Failed to load collections.
            </p>
          ) : !data || data.items.length === 0 ? (
            <p className="p-10 text-center text-muted-foreground text-sm">
              No collections yet. Create one to start merchandising.
            </p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-xs font-semibold text-muted-foreground bg-surface">
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
                {data.items.map((c) => (
                  <motion.tr
                    key={c.id}
                    variants={fadeInUp}
                    className="border-t border-border hover:bg-muted transition-colors"
                  >
                    <td className="px-5 py-3">
                      <Link
                        href={`/admin/collections/${c.id}/products`}
                        className="font-medium text-foreground hover:text-link"
                      >
                        {c.name}
                      </Link>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        /{c.slug}
                      </p>
                    </td>
                    <td className="px-5 py-3">
                      <TypeBadge type={c.type} />
                    </td>
                    <td className="px-5 py-3">
                      <StatusBadge status={c.status} />
                    </td>
                    <td className="px-5 py-3 text-foreground">
                      <span className="inline-flex items-center gap-1.5">
                        <Layers className="w-3.5 h-3.5 text-muted-foreground" />
                        {c.productCount}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-muted-foreground">
                      {c.featured
                        ? `Yes${c.featuredRank != null ? ` · #${c.featuredRank}` : ""}`
                        : "—"}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <div className="inline-flex items-center gap-1">
                        <button
                          type="button"
                          onClick={() =>
                            router.push(`/admin/collections/${c.id}/edit`)
                          }
                          aria-label={`Edit ${c.name}`}
                          className="p-2 rounded-lg text-muted-foreground hover:text-link hover:bg-muted transition-colors"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(c)}
                          disabled={removeMutation.isPending}
                          aria-label={`Delete ${c.name}`}
                          className="p-2 rounded-lg text-muted-foreground hover:text-danger hover:bg-muted transition-colors disabled:opacity-40"
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
          <div className="flex items-center justify-between mt-4 text-xs text-muted-foreground">
            <span>
              Page {data.page + 1} of {data.totalPages} · {data.totalElements}{" "}
              total
            </span>
            <div className="inline-flex gap-2">
              <button
                type="button"
                disabled={!data.hasPrevious}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1.5 rounded-full border border-border hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                type="button"
                disabled={!data.hasNext}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1.5 rounded-full border border-border hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed"
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
