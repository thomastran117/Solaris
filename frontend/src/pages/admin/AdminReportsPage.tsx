import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  Flag, ChevronDown, ChevronUp, ChevronLeft, ChevronRight,
  CheckCircle, XCircle, Search, RefreshCw,
} from "lucide-react";
import NavyGridGlowBackground from "../../components/layout/NavyGridGlowBackground";
import { adminReportsApi } from "../../api/reports";
import { REPORT_REASON_LABELS } from "../../schemas/report";
import { REPORT_STATUSES } from "../../types/report";
import type { Report, ReportReason, ReportStatus, ReportTargetType } from "../../types/report";
import { useAnims } from "../../hooks/useAnims";


const TARGET_TYPE_TABS: { value: ReportTargetType | undefined; label: string }[] = [
  { value: undefined, label: "All" },
  { value: "PRODUCT",  label: "Products" },
  { value: "COMPANY",  label: "Companies" },
  { value: "REVIEW",   label: "Reviews" },
  { value: "USER",     label: "Users" },
];

const STATUS_COLORS: Record<ReportStatus, string> = {
  OPEN:      "border-blue-400/40 bg-blue-500/10 text-blue-300",
  ACTIONED:  "border-green-400/40 bg-green-500/10 text-green-300",
  DISMISSED: "border-white/15 bg-white/[0.04] text-white/50",
};

const TARGET_LABELS: Record<ReportTargetType, string> = {
  PRODUCT: "Product",
  COMPANY: "Company",
  REVIEW:  "Review",
  USER:    "User",
};

function filterPill(active: boolean) {
  return `rounded-full border px-3 py-1 text-xs font-semibold transition ${
    active
      ? "border-blue-400/40 bg-blue-500/15 text-blue-300"
      : "border-white/15 bg-white/[0.04] text-white/70 hover:text-white hover:border-white/30"
  }`;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric", month: "short", day: "numeric",
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
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm overflow-hidden">
      {/* Summary row */}
      <button
        type="button"
        onClick={onToggle}
        className="w-full text-left px-5 py-4 hover:bg-white/[0.03] transition-colors"
      >
        <div className="flex flex-wrap items-center gap-3">
          {/* Target type + name */}
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-2 mb-0.5">
              <span className="text-[10px] font-semibold uppercase tracking-wider text-sky-200/80">
                {TARGET_LABELS[report.targetType]}
              </span>
              <span
                className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold ${STATUS_COLORS[report.status]}`}
              >
                {REPORT_STATUSES[report.status]}
              </span>
            </div>
            <p className="text-sm font-semibold text-white truncate">
              {report.title}
            </p>
            <p className="text-xs text-white/55 truncate mt-0.5">
              {report.targetName ?? report.targetId}
            </p>
          </div>

          {/* Meta */}
          <div className="flex items-center gap-4 flex-shrink-0 text-xs text-white/50">
            <span className="hidden sm:block">
              {REPORT_REASON_LABELS[report.reason]?.label ?? report.reason}
            </span>
            <span className="hidden md:block">
              {report.reporterName ?? "Unknown"}
            </span>
            <span>{formatDate(report.createdAt)}</span>
            {expanded ? (
              <ChevronUp className="h-4 w-4 text-white/30" />
            ) : (
              <ChevronDown className="h-4 w-4 text-white/30" />
            )}
          </div>
        </div>
      </button>

      {/* Expanded detail */}
      {expanded && (
        <div className="border-t border-white/10 px-5 py-4 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs text-white/55">
            <div>
              <span className="text-white/30 uppercase tracking-wider text-[10px]">Reason</span>
              <p className="mt-0.5 text-white/80">
                {REPORT_REASON_LABELS[report.reason]?.label ?? report.reason}
              </p>
            </div>
            <div>
              <span className="text-white/30 uppercase tracking-wider text-[10px]">Reporter</span>
              <p className="mt-0.5 text-white/80">{report.reporterName ?? report.reporterId}</p>
            </div>
            {report.resolvedAt && (
              <div>
                <span className="text-white/30 uppercase tracking-wider text-[10px]">Resolved</span>
                <p className="mt-0.5 text-white/80">{formatDate(report.resolvedAt)}</p>
              </div>
            )}
          </div>

          <div>
            <p className="text-[10px] uppercase tracking-wider text-white/30 mb-1">Description</p>
            <p className="text-sm text-white/80 leading-relaxed whitespace-pre-line">{report.description}</p>
          </div>

          {report.screenshots.length > 0 && (
            <div>
              <p className="text-[10px] uppercase tracking-wider text-white/30 mb-2">
                Screenshots ({report.screenshots.length})
              </p>
              <div className="flex flex-wrap gap-2">
                {report.screenshots.map((s) => (
                  <a key={s.id} href={s.url} target="_blank" rel="noopener noreferrer">
                    <img
                      src={s.url}
                      alt=""
                      className="h-16 w-16 rounded-xl object-cover border border-white/10 hover:border-white/30 transition-colors"
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
                className="inline-flex items-center gap-2 rounded-full border border-green-400/40 bg-green-500/10 px-4 py-1.5 text-xs font-semibold text-green-300 hover:bg-green-500/20 transition disabled:opacity-50"
              >
                <CheckCircle className="h-3.5 w-3.5" />
                Action
              </button>
              <button
                type="button"
                disabled={resolving}
                onClick={() => onResolve("DISMISSED")}
                className="inline-flex items-center gap-2 rounded-full border border-white/20 px-4 py-1.5 text-xs font-semibold text-white/60 hover:bg-white/[0.06] hover:text-white transition disabled:opacity-50"
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
    const t = setTimeout(() => { setDebouncedQ(q); setPage(0); }, 300);
    return () => clearTimeout(t);
  }, [q]);

  const reportsQuery = useQuery({
    queryKey: ["admin", "reports", "search", { q: debouncedQ, targetType, status, reason, sort, page }],
    queryFn: () =>
      adminReportsApi
        .search({ q: debouncedQ || undefined, targetType, status, reason, sort, page, size: 20 })
        .then((r) => r.data),
    placeholderData: (prev) => prev,
  });

  const resolveMutation = useMutation({
    mutationFn: ({ id, resolution }: { id: string; resolution: ReportStatus }) =>
      adminReportsApi.resolve(id, resolution),
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
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />

      <div className="relative mx-auto max-w-5xl px-6 py-12 md:px-10">
        {/* Header */}
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="mb-8">
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div>
              <div className="flex items-center gap-3 mb-1">
                <div className="rounded-xl bg-blue-500/15 border border-white/10 p-2">
                  <Flag className="h-5 w-5 text-sky-200" />
                </div>
                <span className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
                  Admin
                </span>
              </div>
              <h1 className="text-3xl font-extrabold text-white">Reports</h1>
              <p className="mt-1 text-sm text-white/60">
                Review and resolve user-submitted reports across all content types.
              </p>
            </div>
            <button
              type="button"
              onClick={() => reindexMutation.mutate()}
              disabled={reindexMutation.isPending}
              className="inline-flex items-center gap-2 rounded-full border border-white/15 px-4 py-2 text-xs font-semibold text-white/60 hover:text-white hover:border-white/30 transition disabled:opacity-50"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${reindexMutation.isPending ? "animate-spin" : ""}`} />
              {reindexMutation.isPending ? "Reindexing…" : "Reindex"}
            </button>
          </div>
        </motion.div>

        {/* Search */}
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="mb-5">
          <div className="relative max-w-md">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-white/30" />
            <input
              type="text"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search reports…"
              className="w-full rounded-xl border border-white/10 bg-white/[0.04] pl-10 pr-4 py-2.5 text-sm text-white placeholder:text-white/35 focus:border-blue-400/40 focus:outline-none"
            />
          </div>
        </motion.div>

        {/* Target type tabs */}
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="flex flex-wrap gap-2 mb-3">
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
          {(Object.entries(REPORT_STATUSES) as [ReportStatus, string][]).map(([val, label]) => (
            <button
              key={val}
              type="button"
              className={filterPill(status === val)}
              onClick={() => handleFilter(setStatus, status === val ? undefined : val)}
            >
              {label}
            </button>
          ))}

          <div className="flex-1" />

          {/* Reason dropdown */}
          <select
            value={reason ?? ""}
            onChange={(e) => handleFilter(setReason, (e.target.value as ReportReason) || undefined)}
            className="text-sm bg-white/[0.06] border border-white/10 rounded-full px-3 py-1.5 text-white/70 focus:outline-none focus:border-white/20 appearance-none cursor-pointer"
          >
            <option value="" className="bg-slate-900 text-white">All reasons</option>
            {(Object.keys(REPORT_REASON_LABELS) as ReportReason[]).map((r) => (
              <option key={r} value={r} className="bg-slate-900 text-white">
                {REPORT_REASON_LABELS[r].label}
              </option>
            ))}
          </select>

          {/* Sort selector */}
          <select
            value={sort}
            onChange={(e) => handleFilter(setSort, e.target.value as typeof sort)}
            className="text-sm bg-white/[0.06] border border-white/10 rounded-full px-3 py-1.5 text-white/70 focus:outline-none focus:border-white/20 appearance-none cursor-pointer"
          >
            <option value="newest" className="bg-slate-900 text-white">Newest</option>
            <option value="oldest" className="bg-slate-900 text-white">Oldest</option>
            <option value="status" className="bg-slate-900 text-white">By status</option>
          </select>
        </motion.div>

        {/* Loading / error / empty */}
        {reportsQuery.isLoading && (
          <p className="text-sm text-white/50">Loading…</p>
        )}
        {reportsQuery.isError && (
          <p className="text-sm text-red-400">Failed to load reports. Please try again.</p>
        )}
        {!reportsQuery.isLoading && reports.length === 0 && (
          <div className="flex flex-col items-center py-20 text-center">
            <Flag className="h-10 w-10 text-white/15 mb-4" />
            <p className="text-sm text-white/50">No reports found for the selected filters.</p>
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
                  onToggle={() => setExpandedId(expandedId === report.id ? null : report.id)}
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
              className="rounded-full border border-white/15 p-2 text-white/60 hover:text-white hover:border-white/30 disabled:opacity-30 transition-colors"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span className="text-xs text-white/50">
              {page + 1} / {data.totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => p + 1)}
              disabled={!data.hasNext}
              className="rounded-full border border-white/15 p-2 text-white/60 hover:text-white hover:border-white/30 disabled:opacity-30 transition-colors"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
