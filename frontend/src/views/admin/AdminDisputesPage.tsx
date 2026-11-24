"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import {
  ShieldAlert,
  X,
  Plus,
  Clock,
  ChevronLeft,
  ChevronRight,
  FileText,
} from "lucide-react";
import { listOpenDisputes, getDispute, addEvidence } from "../../api/disputes";
import {
  addEvidenceSchema,
  type AddEvidenceFormValues,
} from "../../schemas/dispute";
import type {
  DisputeCase,
  DisputeEvidence,
  DisputeEvidenceType,
  DisputeOutcome,
  DisputeStatus,
} from "../../types/disputes";
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

const STATUS_COLORS: Record<DisputeStatus, string> = {
  OPEN: "bg-warning-subtle text-warning border-warning/40",
  UNDER_REVIEW: "bg-primary/10 text-link border-primary/40",
  CLOSED: "bg-muted text-muted-foreground border-border",
};

const STATUS_LABELS: Record<DisputeStatus, string> = {
  OPEN: "Needs response",
  UNDER_REVIEW: "Under review",
  CLOSED: "Closed",
};

const OUTCOME_COLORS: Record<DisputeOutcome, string> = {
  PENDING: "bg-muted text-muted-foreground border-border",
  WON: "bg-success-subtle text-success border-success/40",
  LOST: "bg-danger-subtle text-danger border-danger/40",
  ACCEPTED: "bg-muted text-muted-foreground border-border",
};

const EVIDENCE_LABELS: Record<DisputeEvidenceType, string> = {
  ORDER_DETAILS: "Order details",
  TRACKING: "Tracking",
  DELIVERY_CONFIRMATION: "Delivery confirmation",
  CUSTOMER_COMMUNICATION: "Customer communication",
  OTHER: "Other",
};

const EVIDENCE_TYPES = Object.keys(EVIDENCE_LABELS) as DisputeEvidenceType[];

const inputCls =
  "w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring";

const labelCls = "block text-xs font-semibold text-muted-foreground mb-1";

function formatAmount(cents: number, currency: string): string {
  return `${(cents / 100).toFixed(2)} ${currency.toUpperCase()}`;
}

/**
 * Milliseconds until the evidence deadline; negative once it has passed.
 *
 * Overdue is decided on the raw milliseconds, not on the day count: `Math.ceil` returns `-0`
 * for anything inside the last 24 hours, and `-0 < 0` is false while `-0 === 0` is true, so a
 * dispute that expired an hour ago would otherwise render as a neutral "due today".
 */
function msUntil(deadline: string): number {
  return new Date(deadline).getTime() - Date.now();
}

function DeadlineLabel({ deadline }: { deadline: string | null }) {
  if (!deadline) {
    return (
      <span className="text-xs text-muted-foreground">
        No deadline supplied
      </span>
    );
  }
  const ms = msUntil(deadline);
  const overdue = ms < 0;
  // Anything inside the next 24h is "due today". Math.ceil never yields 0 for a positive
  // remainder, so keying this off the day count would silently turn six hours into "1 day left".
  const dueToday = !overdue && ms < 86_400_000;
  const days = Math.ceil(ms / 86_400_000);
  const date = new Date(deadline).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
  const tone =
    overdue || dueToday
      ? "text-danger"
      : days <= 3
        ? "text-warning"
        : "text-muted-foreground";
  const suffix = overdue
    ? "overdue"
    : dueToday
      ? "due today"
      : `${days} days left`;
  return (
    <span className={`text-xs flex items-center gap-1.5 ${tone}`}>
      <Clock className="w-3.5 h-3.5" />
      Evidence due {date} · {suffix}
    </span>
  );
}

export default function AdminDisputesPage() {
  const queryClient = useQueryClient();
  const { fadeInUp, stagger, hoverLift } = useAnims();

  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showAddEvidence, setShowAddEvidence] = useState(false);

  // ── Queries ──────────────────────────────────────────────────────────────────

  const {
    data: disputePage,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["disputes", "list", { page }],
    queryFn: () => listOpenDisputes(page),
  });
  const disputes = disputePage?.items ?? [];

  const { data: detail, isLoading: isDetailLoading } = useQuery({
    queryKey: ["disputes", "detail", selectedId],
    queryFn: () => getDispute(selectedId!),
    enabled: !!selectedId,
  });

  // ── Form + mutation ──────────────────────────────────────────────────────────

  const form = useForm<AddEvidenceFormValues>({
    resolver: zodResolver(addEvidenceSchema),
    defaultValues: { evidenceType: "OTHER", content: "", attachmentUrl: "" },
  });

  const evidenceMutation = useMutation({
    mutationFn: (values: AddEvidenceFormValues) =>
      addEvidence(selectedId!, {
        evidenceType: values.evidenceType,
        content: values.content,
        attachmentUrl: values.attachmentUrl || undefined,
      }),
    onSuccess: () => {
      // The list card shows an evidence count, so both queries go stale on a new entry.
      queryClient.invalidateQueries({
        queryKey: ["disputes", "detail", selectedId],
      });
      queryClient.invalidateQueries({ queryKey: ["disputes", "list"] });
      setShowAddEvidence(false);
      form.reset();
    },
  });

  return (
    <div className="px-6 py-10">
      <div className="max-w-5xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <p className="text-xs font-semibold text-link mb-1">Admin</p>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            Chargebacks
          </h1>
          <p className="text-muted-foreground text-sm mt-1">
            Open disputes awaiting evidence. Review the pre-populated pack, add
            anything missing, then submit through the Stripe dashboard before
            the deadline.
          </p>
        </div>

        {/* List */}
        {isLoading ? (
          <div className="text-muted-foreground text-sm py-12 text-center">
            Loading disputes…
          </div>
        ) : isError ? (
          <div className="rounded-xl border border-border bg-surface p-12 text-center">
            <p className="text-danger text-sm">
              Could not load disputes. Please try again.
            </p>
          </div>
        ) : disputes.length === 0 ? (
          <div className="rounded-xl border border-border bg-surface p-12 text-center">
            <ShieldAlert className="w-10 h-10 text-link mx-auto mb-3 opacity-50" />
            <p className="text-muted-foreground text-sm">No open disputes.</p>
          </div>
        ) : (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="space-y-4"
          >
            {disputes.map((d: DisputeCase) => (
              <motion.button
                key={d.id}
                type="button"
                variants={fadeInUp}
                whileHover={hoverLift}
                onClick={() => setSelectedId(d.id)}
                className="w-full text-left rounded-xl border border-border bg-surface shadow-sm hover:shadow-md p-5"
              >
                <div className="flex items-start justify-between gap-4 flex-wrap">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap mb-1">
                      <span
                        className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${STATUS_COLORS[d.status]}`}
                      >
                        {STATUS_LABELS[d.status]}
                      </span>
                      {d.outcome !== "PENDING" && (
                        <span
                          className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${OUTCOME_COLORS[d.outcome]}`}
                        >
                          {d.outcome}
                        </span>
                      )}
                      <span className="text-xs text-muted-foreground font-mono">
                        {d.stripeDisputeId}
                      </span>
                    </div>
                    <p className="font-semibold text-foreground">
                      {formatAmount(d.amountCents, d.currency)}
                      <span className="text-muted-foreground font-normal text-sm">
                        {" "}
                        · {d.reason ?? "reason unspecified"}
                      </span>
                    </p>
                    <div className="mt-1.5">
                      <DeadlineLabel deadline={d.evidenceDeadline} />
                    </div>
                    {!d.orderId && (
                      <p className="text-xs text-danger mt-1">
                        No matching order — reconcile this charge manually.
                      </p>
                    )}
                  </div>

                  <span className="text-xs text-muted-foreground flex items-center gap-1.5 flex-shrink-0">
                    <FileText className="w-3.5 h-3.5 text-link" />
                    {d.evidenceCount} evidence{" "}
                    {d.evidenceCount === 1 ? "entry" : "entries"}
                  </span>
                </div>
              </motion.button>
            ))}
          </motion.div>
        )}

        {/* Pagination */}
        {disputePage && disputePage.totalPages > 1 && (
          <div className="flex items-center justify-between mt-6">
            <p className="text-xs text-muted-foreground">
              {disputePage.totalElements} open disputes
            </p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage((p) => p - 1)}
                disabled={!disputePage.hasPrevious}
                className="p-2 rounded-xl border border-border hover:bg-muted text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition"
                aria-label="Previous page"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="text-xs text-muted-foreground">
                {page + 1} / {disputePage.totalPages}
              </span>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={!disputePage.hasNext}
                className="p-2 rounded-xl border border-border hover:bg-muted text-muted-foreground hover:text-foreground disabled:opacity-30 disabled:cursor-not-allowed transition"
                aria-label="Next page"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ── Dispute detail drawer ──────────────────────────────────────────────── */}
      {selectedId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-overlay p-4 overflow-y-auto">
          <div className="w-full max-w-3xl rounded-xl border border-border bg-surface-raised p-8 shadow-lg my-8">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-foreground">
                Dispute detail
              </h2>
              <button
                onClick={() => {
                  setSelectedId(null);
                  setShowAddEvidence(false);
                  form.reset();
                }}
                className="text-muted-foreground hover:text-foreground transition"
                aria-label="Close dispute detail"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {isDetailLoading || !detail ? (
              <p className="text-muted-foreground text-sm py-8 text-center">
                Loading evidence…
              </p>
            ) : (
              <>
                <div className="rounded-xl border border-border bg-surface p-5 mb-6">
                  <div className="flex items-center gap-2 flex-wrap mb-2">
                    <span
                      className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${STATUS_COLORS[detail.dispute.status]}`}
                    >
                      {STATUS_LABELS[detail.dispute.status]}
                    </span>
                    <span
                      className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${OUTCOME_COLORS[detail.dispute.outcome]}`}
                    >
                      {detail.dispute.outcome}
                    </span>
                    <span className="text-xs text-muted-foreground font-mono">
                      {detail.dispute.stripeDisputeId}
                    </span>
                  </div>
                  <p className="text-lg font-semibold text-foreground">
                    {formatAmount(
                      detail.dispute.amountCents,
                      detail.dispute.currency
                    )}
                  </p>
                  <p className="text-sm text-muted-foreground mt-0.5">
                    Reason: {detail.dispute.reason ?? "unspecified"}
                  </p>
                  <div className="mt-2">
                    <DeadlineLabel deadline={detail.dispute.evidenceDeadline} />
                  </div>
                  {detail.dispute.orderId && (
                    <p className="text-xs text-muted-foreground mt-2 font-mono">
                      Order {detail.dispute.orderId}
                    </p>
                  )}
                </div>

                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-lg font-semibold text-foreground">
                    Evidence ({detail.evidence.length})
                  </h3>
                  <button
                    onClick={() => setShowAddEvidence((v) => !v)}
                    className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-primary-foreground text-sm font-semibold px-4 py-2 rounded-full transition"
                  >
                    <Plus className="w-4 h-4" />
                    Add evidence
                  </button>
                </div>

                {showAddEvidence && (
                  <form
                    onSubmit={form.handleSubmit((v) =>
                      evidenceMutation.mutate(v)
                    )}
                    className="rounded-xl border border-border bg-surface p-5 mb-6 space-y-4"
                  >
                    <div>
                      <label className={labelCls} htmlFor="evidenceType">
                        Type
                      </label>
                      <select
                        id="evidenceType"
                        {...form.register("evidenceType")}
                        className={inputCls}
                      >
                        {EVIDENCE_TYPES.map((t) => (
                          <option key={t} value={t}>
                            {EVIDENCE_LABELS[t]}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div>
                      <label className={labelCls} htmlFor="content">
                        Content
                      </label>
                      <textarea
                        id="content"
                        {...form.register("content")}
                        rows={6}
                        placeholder="Paste the evidence to submit to Stripe…"
                        className={inputCls}
                      />
                      {form.formState.errors.content && (
                        <p className="text-danger text-xs mt-1">
                          {form.formState.errors.content.message}
                        </p>
                      )}
                    </div>

                    <div>
                      <label className={labelCls} htmlFor="attachmentUrl">
                        Attachment URL (optional)
                      </label>
                      <input
                        id="attachmentUrl"
                        {...form.register("attachmentUrl")}
                        className={inputCls}
                      />
                      {form.formState.errors.attachmentUrl && (
                        <p className="text-danger text-xs mt-1">
                          {form.formState.errors.attachmentUrl.message}
                        </p>
                      )}
                    </div>

                    {evidenceMutation.isError && (
                      <p className="text-danger text-xs">
                        {apiErrorMessage(
                          evidenceMutation.error,
                          "Failed to add evidence. Please try again."
                        )}
                      </p>
                    )}

                    <button
                      type="submit"
                      disabled={evidenceMutation.isPending}
                      className="w-full bg-primary hover:bg-primary-hover disabled:opacity-50 text-primary-foreground font-semibold py-2.5 rounded-full transition"
                    >
                      {evidenceMutation.isPending ? "Saving…" : "Save evidence"}
                    </button>
                  </form>
                )}

                {detail.evidence.length === 0 ? (
                  <p className="text-muted-foreground text-sm py-8 text-center">
                    No evidence on this case yet.
                  </p>
                ) : (
                  <div className="space-y-4">
                    {detail.evidence.map((e: DisputeEvidence) => (
                      <div
                        key={e.id}
                        className="rounded-xl border border-border bg-surface p-5"
                      >
                        <div className="flex items-center gap-2 flex-wrap mb-2">
                          <span className="text-xs font-semibold px-2 py-0.5 rounded-full border border-border bg-primary/10 text-link">
                            {EVIDENCE_LABELS[e.evidenceType]}
                          </span>
                          <span className="text-xs text-muted-foreground">
                            {e.createdById
                              ? "Added by staff"
                              : "Auto-generated"}
                          </span>
                        </div>
                        <pre className="text-sm text-foreground leading-relaxed whitespace-pre-wrap font-sans overflow-x-auto">
                          {e.content}
                        </pre>
                        {e.attachmentUrl && (
                          <a
                            href={e.attachmentUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="text-xs text-link hover:underline mt-2 inline-block"
                          >
                            View attachment
                          </a>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
