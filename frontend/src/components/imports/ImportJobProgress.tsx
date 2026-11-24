import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CheckCircle2,
  AlertTriangle,
  Loader2,
  XCircle,
  Download,
} from "lucide-react";
import { importsApi } from "../../api/imports";
import { Progress } from "../ui/Progress";
import {
  TERMINAL_STATUSES,
  type ImportJob,
  type ImportJobStatus,
} from "../../types/imports";

function statusTone(status: ImportJobStatus) {
  switch (status) {
    case "COMPLETED":
      return "text-success border-success/40 bg-success-subtle";
    case "COMPLETED_WITH_ERRORS":
      return "text-warning border-warning/40 bg-warning-subtle";
    case "FAILED":
      return "text-danger border-danger/40 bg-danger-subtle";
    default:
      return "text-link border-primary/40 bg-primary/10";
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
  companyId: string;
  jobId: string;
  onTerminal?: (job: ImportJob) => void;
}

export default function ImportJobProgress({
  companyId,
  jobId,
  onTerminal,
}: Props) {
  const queryClient = useQueryClient();

  const { data: job } = useQuery({
    queryKey: ["imports", companyId, jobId],
    queryFn: () => importsApi.get(companyId, jobId).then((r) => r.data),
    refetchInterval: (query) => {
      const status = (query.state.data as ImportJob | undefined)?.status;
      if (status && TERMINAL_STATUSES.includes(status)) return false;
      return 2000;
    },
  });

  useEffect(() => {
    if (!job) return;
    if (TERMINAL_STATUSES.includes(job.status)) {
      queryClient.invalidateQueries({ queryKey: ["admin-products", "list"] });
      queryClient.invalidateQueries({
        queryKey: ["imports", companyId, "list"],
      });
      onTerminal?.(job);
    }
  }, [job, onTerminal, queryClient, companyId]);

  if (!job) {
    return (
      <div className="rounded-xl border border-border bg-surface shadow-sm p-6">
        <div className="h-6 w-40 bg-muted rounded animate-pulse" />
      </div>
    );
  }

  const pct = Math.max(0, Math.min(100, job.progressPercent));

  async function handleDownloadErrors() {
    const r = await importsApi.errorReport(companyId, jobId);
    window.open(r.data.url, "_blank");
  }

  return (
    <div className="rounded-xl border border-border bg-surface shadow-sm p-6">
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="flex items-center gap-3">
          <span
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${statusTone(
              job.status
            )}`}
          >
            <StatusIcon status={job.status} />
            {job.status.replace(/_/g, " ")}
          </span>
          <span className="text-xs text-muted-foreground">Job #{job.id}</span>
        </div>
        <div className="text-xs text-muted-foreground">
          {job.processedRows} / {job.totalRows || "?"} rows
        </div>
      </div>

      <Progress
        value={pct}
        label="Import progress"
        valueText={`${job.processedRows} of ${job.totalRows || "unknown"} rows processed`}
      />

      <div className="flex items-center justify-between mt-3 text-xs">
        <div className="flex items-center gap-4">
          <span className="text-success">{job.successRows} succeeded</span>
          <span className="text-danger">{job.errorRows} failed</span>
        </div>
        {job.hasErrorReport && (
          <button
            type="button"
            onClick={handleDownloadErrors}
            className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-border text-foreground hover:bg-muted transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
            Error report
          </button>
        )}
      </div>

      {job.failureReason && (
        <p className="mt-3 text-xs text-danger">{job.failureReason}</p>
      )}
    </div>
  );
}
