"use client";

import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  Flag,
  ChevronDown,
  ChevronUp,
  ChevronLeft,
  ChevronRight,
  CheckCircle,
  XCircle,
  Search,
  RefreshCw,
} from "lucide-react";
import { adminReportsApi } from "../../api/reports";
import { REPORT_REASON_LABELS } from "../../schemas/report";
import { REPORT_STATUSES } from "../../types/report";
import type {
  Report,
  ReportReason,
  ReportStatus,
  ReportTargetType,
} from "../../types/report";
import { useAnims } from "../../hooks/useAnims";

const TARGET_TYPE_TABS: {
  value: ReportTargetType | undefined;
  label: string;
}[] = [
  { value: undefined, label: "All" },
  { value: "PRODUCT", label: "Products" },
  { value: "COMPANY", label: "Companies" },
  { value: "REVIEW", label: "Reviews" },
  { value: "USER", label: "Users" },
];

const STATUS_COLORS: Record<ReportStatus, string> = {
  OPEN: "border-primary/40 bg-primary/10 text-link",
  ACTIONED: "border-success/40 bg-success-subtle text-success",
  DISMISSED: "border-border bg-surface text-muted-foreground",
};

const TARGET_LABELS: Record<ReportTargetType, string> = {
  PRODUCT: "Product",
  COMPANY: "Company",
  REVIEW: "Review",
  USER: "User",
};

function filterPill(active: boolean) {
  return `rounded-full border px-3 py-1 text-xs font-semibold transition ${
    active
      ? "border-primary/40 bg-primary/10 text-link"
      : "border-border bg-surface text-muted-foreground hover:text-foreground hover:border-input"
  }`;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function ReportRow({
  report,
  expanded,
  onToggle,
  onResolve,
  resolving,
}: {
  report: Report;
  expanded: boolean;
  onToggle: () => void;
  onResolve: (resolution: ReportStatus) => void;
  resolving: boolean;
}) {
  return (
    <div className="rounded-xl border border-border bg-surface shadow-sm overflow-hidden">
      {/* Summary row */}
      <button
        type="button"
        onClick={onToggle}
        className="w-full text-left px-5 py-4 hover:bg-muted transition-colors"
      >
        <div className="flex flex-wrap items-center gap-3">
          {/* Target type + name */}
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-2 mb-0.5">
              <span className="text-[10px] font-semibold text-link">
                {TARGET_LABELS[report.targetType]}
              </span>
              <span
                className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold ${STATUS_COLORS[report.status]}`}
              >
                {REPORT_STATUSES[report.status]}
              </span>
            </div>
            <p className="text-sm font-semibold text-foreground truncate">
              {report.title}
            </p>
            <p className="text-xs text-muted-foreground truncate mt-0.5">
              {report.targetName ?? report.targetId}
            </p>
          </div>

          {/* Meta */}
          <div className="flex items-center gap-4 flex-shrink-0 text-xs text-muted-foreground">
            <span className="hidden sm:block">
              {REPORT_REASON_LABELS[report.reason]?.label ?? report.reason}
            </span>
            <span className="hidden md:block">
              {report.reporterName ?? "Unknown"}
            </span>
            <span>{formatDate(report.createdAt)}</span>
            {expanded ? (
              <ChevronUp className="h-4 w-4 text-muted-foreground" />
            ) : (
              <ChevronDown className="h-4 w-4 text-muted-foreground" />
            )}
          </div>
        </div>
      </button>

      {/* Expanded detail */}
      {expanded && (
        <div className="border-t border-border px-5 py-4 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs text-muted-foreground">
            <div>
              <span className="text-muted-foreground text-[10px]">Reason</span>
              <p className="mt-0.5 text-foreground">
                {REPORT_REASON_LABELS[report.reason]?.label ?? report.reason}
              </p>
            </div>
            <div>
              <span className="text-muted-foreground text-[10px]">
                Reporter
              </span>
              <p className="mt-0.5 text-foreground">
                {report.reporterName ?? report.reporterId}
              </p>
            </div>
            {report.resolvedAt && (
              <div>
                <span className="text-muted-foreground text-[10px]">
                  Resolved
                </span>
                <p className="mt-0.5 text-foreground">
                  {formatDate(report.resolvedAt)}
                </p>
              </div>
            )}
          </div>

          <div>
            <p className="text-[10px] text-muted-foreground mb-1">
              Description
            </p>
            <p className="text-sm text-foreground leading-relaxed whitespace-pre-line">
              {report.description}
            </p>
          </div>

          {report.screenshots.length > 0 && (
            <div>
              <p className="text-[10px] text-muted-foreground mb-2">
                Screenshots ({report.screenshots.length})
              </p>
              <div className="flex flex-wrap gap-2">
                {report.screenshots.map((s) => (
                  <a
                    key={s.id}
                    href={s.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <img
                      src={s.url}
                      alt=""
                      className="h-16 w-16 rounded-xl object-cover border border-border hover:border-input transition-colors"
                    />
                  </a>
                ))}
              </div>
            </div>
          )}

          {report.status === "OPEN" && (
            <div className="flex flex-wrap gap-2 pt-1">
              <button
                type="button"
                disabled={resolving}
                onClick={() => onResolve("ACTIONED")}
                className="inline-flex items-center gap-2 rounded-full border border-success/40 bg-success-subtle px-4 py-1.5 text-xs font-semibold text-success hover:bg-success-subtle transition disabled:opacity-50"
              >
                <CheckCircle className="h-3.5 w-3.5" />
                Action
              </button>
              <button
                type="button"
                disabled={resolving}
                onClick={() => onResolve("DISMISSED")}
                className="inline-flex items-center gap-2 rounded-full border border-input px-4 py-1.5 text-xs font-semibold text-muted-foreground hover:bg-muted hover:text-foreground transition disabled:opacity-50"
              >
                <XCircle className="h-3.5 w-3.5" />
                Dismiss
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function AdminReportsPage() {
  const { fadeInUp, stagger } = useAnims();
  const queryClient = useQueryClient();

  const [q, setQ] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const [targetType, setTargetType] = useState<ReportTargetType | undefined>();
  const [status, setStatus] = useState<ReportStatus | undefined>();
  const [reason, setReason] = useState<ReportReason | undefined>();
  const [sort, setSort] = useState<"newest" | "oldest" | "status">("newest");
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedQ(q);
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [q]);

  const reportsQuery = useQuery({
    queryKey: [
      "admin",
      "reports",
      "search",
      { q: debouncedQ, targetType, status, reason, sort, page },
    ],
    queryFn: () =>
      adminReportsApi
        .search({
          q: debouncedQ || undefined,
          targetType,
          status,
          reason,
          sort,
          page,
          size: 20,
        })
        .then((r) => r.data),
    placeholderData: (prev) => prev,
  });

  const resolveMutation = useMutation({
    mutationFn: ({
      id,
      resolution,
    }: {
      id: string;
      resolution: ReportStatus;
    }) => adminReportsApi.resolve(id, resolution),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "reports"] });
      setExpandedId(null);
    },
  });

  const reindexMutation = useMutation({
    mutationFn: () => adminReportsApi.reindex(),
  });

  function handleFilter<T>(setter: (v: T) => void, value: T) {
    setter(value);
    setPage(0);
  }

  const data = reportsQuery.data;
  const reports: Report[] = data?.items ?? [];

  return (
    <div className="text-foreground">
      <div className="relative mx-auto max-w-5xl px-6 py-12 md:px-10">
        {/* Header */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="mb-8"
        >
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div>
              <div className="flex items-center gap-3 mb-1">
                <div className="rounded-xl bg-primary/10 border border-border p-2">
                  <Flag className="h-5 w-5 text-link" />
                </div>
                <span className="text-xs font-semibold text-link">Admin</span>
              </div>
              <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                Reports
              </h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Review and resolve user-submitted reports across all content
                types.
              </p>
            </div>
            <button
              type="button"
              onClick={() => reindexMutation.mutate()}
              disabled={reindexMutation.isPending}
              className="inline-flex items-center gap-2 rounded-full border border-border px-4 py-2 text-xs font-semibold text-muted-foreground hover:text-foreground hover:border-input transition disabled:opacity-50"
            >
              <RefreshCw
                className={`h-3.5 w-3.5 ${reindexMutation.isPending ? "animate-spin" : ""}`}
              />
              {reindexMutation.isPending ? "Reindexing…" : "Reindex"}
            </button>
          </div>
        </motion.div>

        {/* Search */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="mb-5"
        >
          <div className="relative max-w-md">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search reports…"
              className="w-full rounded-xl border border-border bg-surface pl-10 pr-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:border-ring focus:outline-none"
            />
          </div>
        </motion.div>

        {/* Target type tabs */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="flex flex-wrap gap-2 mb-3"
        >
          {TARGET_TYPE_TABS.map((tab) => (
            <button
              key={tab.label}
              type="button"
              className={filterPill(targetType === tab.value)}
              onClick={() => handleFilter(setTargetType, tab.value)}
            >
              {tab.label}
            </button>
          ))}
        </motion.div>

        {/* Status + reason + sort filters */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="flex flex-wrap items-center gap-2 mb-8"
        >
          {(Object.entries(REPORT_STATUSES) as [ReportStatus, string][]).map(
            ([val, label]) => (
              <button
                key={val}
                type="button"
                className={filterPill(status === val)}
                onClick={() =>
                  handleFilter(setStatus, status === val ? undefined : val)
                }
              >
                {label}
              </button>
            )
          )}

          <div className="flex-1" />

          {/* Reason dropdown */}
          <select
            value={reason ?? ""}
            onChange={(e) =>
              handleFilter(
                setReason,
                (e.target.value as ReportReason) || undefined
              )
            }
            className="text-sm bg-surface border border-border rounded-full px-3 py-1.5 text-muted-foreground focus:outline-none focus:border-ring appearance-none cursor-pointer"
          >
            <option value="" className="bg-surface text-foreground">
              All reasons
            </option>
            {(Object.keys(REPORT_REASON_LABELS) as ReportReason[]).map((r) => (
              <option key={r} value={r} className="bg-surface text-foreground">
                {REPORT_REASON_LABELS[r].label}
              </option>
            ))}
          </select>

          {/* Sort selector */}
          <select
            value={sort}
            onChange={(e) =>
              handleFilter(setSort, e.target.value as typeof sort)
            }
            className="text-sm bg-surface border border-border rounded-full px-3 py-1.5 text-muted-foreground focus:outline-none focus:border-ring appearance-none cursor-pointer"
          >
            <option value="newest" className="bg-surface text-foreground">
              Newest
            </option>
            <option value="oldest" className="bg-surface text-foreground">
              Oldest
            </option>
            <option value="status" className="bg-surface text-foreground">
              By status
            </option>
          </select>
        </motion.div>

        {/* Loading / error / empty */}
        {reportsQuery.isLoading && (
          <p className="text-sm text-muted-foreground">Loading…</p>
        )}
        {reportsQuery.isError && (
          <p className="text-sm text-danger">
            Failed to load reports. Please try again.
          </p>
        )}
        {!reportsQuery.isLoading && reports.length === 0 && (
          <div className="flex flex-col items-center py-20 text-center">
            <Flag className="h-10 w-10 text-muted-foreground mb-4" />
            <p className="text-sm text-muted-foreground">
              No reports found for the selected filters.
            </p>
          </div>
        )}

        {/* Report list */}
        {reports.length > 0 && (
          <motion.div
            variants={stagger}
            initial="hidden"
            animate="visible"
            className="flex flex-col gap-3"
          >
            {reports.map((report) => (
              <motion.div key={report.id} variants={fadeInUp}>
                <ReportRow
                  report={report}
                  expanded={expandedId === report.id}
                  onToggle={() =>
                    setExpandedId(expandedId === report.id ? null : report.id)
                  }
                  onResolve={(resolution) =>
                    resolveMutation.mutate({ id: report.id, resolution })
                  }
                  resolving={resolveMutation.isPending}
                />
              </motion.div>
            ))}
          </motion.div>
        )}

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 mt-8">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={!data.hasPrevious}
              className="rounded-full border border-border p-2 text-muted-foreground hover:text-foreground hover:border-input disabled:opacity-30 transition-colors"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span className="text-xs text-muted-foreground">
              {page + 1} / {data.totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => p + 1)}
              disabled={!data.hasNext}
              className="rounded-full border border-border p-2 text-muted-foreground hover:text-foreground hover:border-input disabled:opacity-30 transition-colors"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
