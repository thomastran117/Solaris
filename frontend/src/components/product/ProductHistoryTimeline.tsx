import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { AlertCircle, RotateCcw, User as UserIcon, Box } from "lucide-react";
import { useAnims } from "../../hooks/useAnims";
import {
  adminProductsApi,
  type CompanyRole,
  type ProductHistoryEntry,
  type ProductHistoryPage,
} from "../../api/catalog";

function roleBadge(
  role: CompanyRole | null
): { label: string; cls: string } | null {
  if (!role) return null;
  switch (role) {
    case "OWNER":
      return { label: "Owner", cls: "text-link border-primary/40" };
    case "MANAGER":
      return { label: "Manager", cls: "text-link border-primary/40" };
    case "EMPLOYEE":
      return { label: "Employee", cls: "text-muted-foreground border-input" };
    default:
      return null;
  }
}

interface Props {
  companyId: string;
  productId: string;
  expectedVersion?: number | null;
  onReverted?: () => void;
}

function formatTime(iso: string) {
  const d = new Date(iso);
  return d.toLocaleString();
}

function dayKey(iso: string) {
  const d = new Date(iso);
  return d.toLocaleDateString();
}

function describeEntry(e: ProductHistoryEntry): string {
  if (e.kind === "INVENTORY_ADJUSTMENT") {
    const sign = (e.delta ?? 0) >= 0 ? "+" : "";
    return `Stock ${sign}${e.delta} (${e.previousStock} → ${e.newStock}) — ${e.reason ?? "ADJUSTMENT"}`;
  }
  if (e.changeType === "CREATED")
    return `Set ${e.fieldName} = ${e.newValue ?? "—"}`;
  if (e.changeType === "DELETED")
    return `Deleted ${e.variantId == null ? "product" : "variant"}`;
  return `${e.fieldName}: ${e.oldValue ?? "—"} → ${e.newValue ?? "—"}`;
}

function sourceBadge(s: string | null): { label: string; cls: string } {
  switch (s) {
    case "REVERT":
      return { label: "Revert", cls: "text-warning border-warning/40" };
    case "SYSTEM":
      return { label: "System", cls: "text-muted-foreground border-input" };
    case "USER":
    default:
      return { label: "User", cls: "text-link border-primary/40" };
  }
}

export default function ProductHistoryTimeline({
  companyId,
  productId,
  expectedVersion,
  onReverted,
}: Props) {
  const { fadeInUp, stagger } = useAnims();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [filterKind, setFilterKind] = useState<
    "ALL" | "FIELD_CHANGE" | "INVENTORY_ADJUSTMENT"
  >("ALL");
  const [confirming, setConfirming] = useState<ProductHistoryEntry | null>(
    null
  );

  const queryKey = ["products", "history", { companyId, productId, page }];
  const { data, isLoading, error } = useQuery<ProductHistoryPage>({
    queryKey,
    queryFn: () =>
      adminProductsApi
        .getHistory(companyId, productId, { page, size: 20 })
        .then((r) => r.data),
    enabled: !!companyId && !!productId,
  });

  const revertMutation = useMutation({
    mutationFn: (entry: ProductHistoryEntry) =>
      adminProductsApi
        .revertChanges(companyId, productId, {
          logEntryIds: [entry.id],
          expectedVersion: expectedVersion ?? null,
        })
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["products", "history", { companyId, productId }],
      });
      queryClient.invalidateQueries({
        queryKey: ["admin-products", "detail", { companyId, productId }],
      });
      setConfirming(null);
      onReverted?.();
    },
  });

  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-surface shadow-sm p-8 flex items-center justify-center">
        <div className="w-6 h-6 rounded-full border-2 border-primary/40 border-t-primary/40 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border border-border bg-surface shadow-sm p-6">
        <p className="inline-flex items-center gap-2 text-sm text-danger">
          <AlertCircle className="w-4 h-4" />
          {(error as Error).message}
        </p>
      </div>
    );
  }

  const items = (data?.items ?? []).filter((e) =>
    filterKind === "ALL" ? true : e.kind === filterKind
  );

  const grouped = items.reduce<Record<string, ProductHistoryEntry[]>>(
    (acc, e) => {
      const k = dayKey(e.occurredAt);
      (acc[k] ??= []).push(e);
      return acc;
    },
    {}
  );

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2 flex-wrap">
        {(["ALL", "FIELD_CHANGE", "INVENTORY_ADJUSTMENT"] as const).map((k) => (
          <button
            key={k}
            type="button"
            onClick={() => setFilterKind(k)}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
              filterKind === k
                ? "border-primary/40 bg-muted text-foreground"
                : "border-border text-muted-foreground hover:bg-muted"
            }`}
          >
            {k === "ALL"
              ? "All"
              : k === "FIELD_CHANGE"
                ? "Catalog edits"
                : "Stock"}
          </button>
        ))}
      </div>

      {items.length === 0 ? (
        <div className="rounded-xl border border-border bg-surface shadow-sm p-8 text-center text-sm text-muted-foreground">
          No history yet — edit the product to see entries here.
        </div>
      ) : (
        <motion.div
          variants={stagger}
          initial="hidden"
          animate="visible"
          className="space-y-6"
        >
          {Object.entries(grouped).map(([day, dayEntries]) => (
            <section key={day}>
              <h3 className="text-xs font-semibold text-link mb-3">{day}</h3>
              <ul className="space-y-2">
                {dayEntries.map((entry) => {
                  const badge = sourceBadge(entry.source);
                  const isField = entry.kind === "FIELD_CHANGE";
                  const isStock = entry.kind === "INVENTORY_ADJUSTMENT";
                  return (
                    <motion.li
                      key={`${entry.kind}-${entry.id}`}
                      variants={fadeInUp}
                      className="rounded-xl border border-border bg-surface shadow-sm hover:shadow-md transition-shadow p-4 flex items-start gap-3"
                    >
                      <div className="shrink-0 mt-0.5 w-8 h-8 rounded-xl bg-primary/10 border border-border flex items-center justify-center">
                        {isStock ? (
                          <Box className="w-4 h-4 text-link" />
                        ) : (
                          <UserIcon className="w-4 h-4 text-link" />
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-semibold text-foreground">
                            {describeEntry(entry)}
                          </span>
                          {isField && (
                            <span
                              className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border ${badge.cls}`}
                            >
                              {badge.label}
                            </span>
                          )}
                          {entry.variantId != null && (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold border border-border text-muted-foreground">
                              variant #{entry.variantId}
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-muted-foreground mt-1 flex items-center gap-2 flex-wrap">
                          <span>
                            {entry.actorUserId == null
                              ? "system"
                              : `user #${entry.actorUserId}`}
                          </span>
                          {(() => {
                            const rb = roleBadge(entry.actorRole);
                            return rb ? (
                              <span
                                className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border ${rb.cls}`}
                              >
                                {rb.label}
                              </span>
                            ) : null;
                          })()}
                          <span>· {formatTime(entry.occurredAt)}</span>
                          {entry.note ? <span>· {entry.note}</span> : null}
                        </div>
                      </div>
                      {isField &&
                        entry.changeType === "UPDATED" &&
                        entry.source !== "REVERT" && (
                          <button
                            type="button"
                            onClick={() => setConfirming(entry)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-semibold border border-input text-foreground hover:bg-muted transition-colors"
                          >
                            <RotateCcw className="w-3.5 h-3.5" />
                            Revert
                          </button>
                        )}
                    </motion.li>
                  );
                })}
              </ul>
            </section>
          ))}
        </motion.div>
      )}

      {(data?.totalPages ?? 0) > 1 && (
        <div className="flex items-center justify-between text-sm">
          <button
            type="button"
            disabled={!data?.hasPrevious}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="px-4 py-2 rounded-full border border-input text-foreground hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Previous
          </button>
          <span className="text-muted-foreground">
            Page {page + 1} of {data?.totalPages}
          </span>
          <button
            type="button"
            disabled={!data?.hasNext}
            onClick={() => setPage((p) => p + 1)}
            className="px-4 py-2 rounded-full border border-input text-foreground hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Next
          </button>
        </div>
      )}

      {confirming && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-overlay px-4"
          onClick={() => setConfirming(null)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="rounded-xl border border-border bg-surface shadow-sm p-8 max-w-md w-full"
          >
            <h4 className="text-lg font-semibold text-foreground mb-2">
              Revert this change?
            </h4>
            <p className="text-sm text-muted-foreground leading-relaxed mb-4">
              <span className="font-semibold text-foreground">
                {confirming.fieldName}
              </span>{" "}
              will be set back from{" "}
              <span className="text-danger">{confirming.newValue ?? "—"}</span>{" "}
              to{" "}
              <span className="text-success">{confirming.oldValue ?? "—"}</span>
              .
            </p>
            {(revertMutation.error as Error | null) && (
              <p className="inline-flex items-center gap-2 text-sm text-danger mb-3">
                <AlertCircle className="w-4 h-4" />
                {(revertMutation.error as Error).message}
              </p>
            )}
            <div className="flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setConfirming(null)}
                className="px-4 py-2 rounded-full border border-input text-sm font-semibold text-foreground hover:bg-muted transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => revertMutation.mutate(confirming)}
                disabled={revertMutation.isPending}
                className="inline-flex items-center gap-2 px-5 py-2 rounded-full bg-primary hover:bg-primary-hover text-sm font-semibold text-primary-foreground transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <RotateCcw className="w-4 h-4" />
                {revertMutation.isPending ? "Reverting…" : "Confirm revert"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
