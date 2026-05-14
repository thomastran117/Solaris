import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion, useReducedMotion } from "framer-motion";
import { CheckCircle2, AlertTriangle, Loader2, XCircle, Download } from "lucide-react";
import { importsApi } from "../../api/imports";
import { TERMINAL_STATUSES, type ImportJob, type ImportJobStatus } from "../../types/imports";

function statusTone(status: ImportJobStatus) {
  switch (status) {
    case "COMPLETED":
      return "text-green-400 border-green-400/30 bg-green-500/15";
    case "COMPLETED_WITH_ERRORS":
      return "text-yellow-400 border-yellow-400/30 bg-yellow-500/15";
    case "FAILED":
      return "text-red-400 border-red-400/30 bg-red-500/15";
    default:
      return "text-sky-200 border-sky-400/30 bg-sky-500/15";
  }
}

function StatusIcon({ status }: { status: ImportJobStatus }) {
  switch (status) {
    case "COMPLETED":
      return <CheckCircle2 className="w-4 h-4" />;
    case "COMPLETED_WITH_ERRORS":
      return <AlertTriangle className="w-4 h-4" />;
    case "FAILED":
      return <XCircle className="w-4 h-4" />;
    default:
      return <Loader2 className="w-4 h-4 animate-spin" />;
  }
}

interface Props {
  companyId: number;
  jobId: number;
  onTerminal?: (job: ImportJob) => void;
}

export default function ImportJobProgress({ companyId, jobId, onTerminal }: Props) {
  const prefersReducedMotion = useReducedMotion();
  const queryClient = useQueryClient();

  const { data: job } = useQuery({
    queryKey: ["imports", companyId, jobId],
    queryFn: () => importsApi.get(companyId, jobId).then(r => r.data),
    refetchInterval: query => {
      const status = (query.state.data as ImportJob | undefined)?.status;
      if (status && TERMINAL_STATUSES.includes(status)) return false;
      return 2000;
    },
  });

  useEffect(() => {
    if (!job) return;
    if (TERMINAL_STATUSES.includes(job.status)) {
      queryClient.invalidateQueries({ queryKey: ["admin-products", "list"] });
      queryClient.invalidateQueries({ queryKey: ["imports", companyId, "list"] });
      onTerminal?.(job);
    }
  }, [job, onTerminal, queryClient, companyId]);

  if (!job) {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
        <div className="h-6 w-40 bg-white/10 rounded animate-pulse" />
      </div>
    );
  }

  const pct = Math.max(0, Math.min(100, job.progressPercent));

  async function handleDownloadErrors() {
    const r = await importsApi.errorReport(companyId, jobId);
    window.open(r.data.url, "_blank");
  }

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="flex items-center gap-3">
          <span
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${statusTone(
              job.status,
            )}`}
          >
            <StatusIcon status={job.status} />
            {job.status.replace(/_/g, " ")}
          </span>
          <span className="text-xs text-white/60">Job #{job.id}</span>
        </div>
        <div className="text-xs text-white/60">
          {job.processedRows} / {job.totalRows || "?"} rows
        </div>
      </div>

      <div className="h-2 rounded-full bg-white/10 overflow-hidden">
        <motion.div
          className="h-full bg-gradient-to-r from-sky-400 to-blue-500"
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={prefersReducedMotion ? { duration: 0 } : { duration: 0.4 }}
        />
      </div>

      <div className="flex items-center justify-between mt-3 text-xs">
        <div className="flex items-center gap-4">
          <span className="text-green-300">{job.successRows} succeeded</span>
          <span className="text-red-300">{job.errorRows} failed</span>
        </div>
        {job.hasErrorReport && (
          <button
            type="button"
            onClick={handleDownloadErrors}
            className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-white/15 text-white/80 hover:bg-white/10 transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
            Error report
          </button>
        )}
      </div>

      {job.failureReason && (
        <p className="mt-3 text-xs text-red-300">{job.failureReason}</p>
      )}
    </div>
  );
}
