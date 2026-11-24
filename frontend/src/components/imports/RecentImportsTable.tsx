import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Loader2, Download, ChevronRight, ChevronDown } from "lucide-react";
import { importsApi } from "../../api/imports";
import type { ImportJob, ImportJobStatus } from "../../types/imports";

function StatusPill({ status }: { status: ImportJobStatus }) {
  const tone: Record<ImportJobStatus, string> = {
    PENDING: "bg-muted border-border text-muted-foreground",
    PARSING: "bg-primary/10 border-primary/40 text-link",
    VALIDATING: "bg-primary/10 border-primary/40 text-link",
    PROCESSING: "bg-primary/10 border-primary/40 text-link",
    COMPLETED: "bg-success-subtle border-success/40 text-success",
    COMPLETED_WITH_ERRORS: "bg-warning-subtle border-warning/40 text-warning",
    FAILED: "bg-danger-subtle border-danger/40 text-danger",
  };
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${tone[status]}`}
    >
      {status.replace(/_/g, " ")}
    </span>
  );
}

function ErrorRowsDrawer({
  companyId,
  jobId,
}: {
  companyId: string;
  jobId: string;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["imports", companyId, jobId, "errors"],
    queryFn: () =>
      importsApi.errors(companyId, jobId, { size: 50 }).then((r) => r.data),
  });
  if (isLoading) {
    return <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />;
  }
  if (!data || data.items.length === 0) {
    return (
      <p className="text-xs text-muted-foreground">No error rows recorded.</p>
    );
  }
  return (
    <div className="mt-2 max-h-60 overflow-y-auto rounded-xl border border-border bg-surface">
      <table className="w-full text-xs">
        <thead className="text-muted-foreground">
          <tr>
            <th className="px-3 py-2 text-left">Row</th>
            <th className="px-3 py-2 text-left">SKU</th>
            <th className="px-3 py-2 text-left">Error</th>
          </tr>
        </thead>
        <tbody>
          {data.items.map((r) => (
            <tr key={r.id} className="border-t border-border">
              <td className="px-3 py-1.5 text-muted-foreground">
                {r.rowNumber}
              </td>
              <td className="px-3 py-1.5 text-foreground">{r.sku ?? "—"}</td>
              <td className="px-3 py-1.5 text-danger">{r.errorMessage}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ExpandableRow({
  companyId,
  job,
}: {
  companyId: string;
  job: ImportJob;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <tr className="border-t border-border hover:bg-muted transition-colors">
        <td className="px-4 py-2.5 text-muted-foreground">#{job.id}</td>
        <td className="px-4 py-2.5 text-foreground">
          {job.jobType.replace(/_/g, " ")}
        </td>
        <td className="px-4 py-2.5">
          <StatusPill status={job.status} />
        </td>
        <td className="px-4 py-2.5 text-foreground">
          {job.successRows} / {job.errorRows} / {job.totalRows}
        </td>
        <td className="px-4 py-2.5 text-muted-foreground text-xs">
          {new Date(job.createdAt).toLocaleString()}
        </td>
        <td className="px-4 py-2.5 text-right">
          <div className="inline-flex items-center gap-1">
            {job.hasErrorReport && (
              <button
                type="button"
                onClick={async () => {
                  const r = await importsApi.errorReport(companyId, job.id);
                  window.open(r.data.url, "_blank");
                }}
                className="p-1.5 rounded-lg text-muted-foreground hover:text-link hover:bg-muted transition-colors"
                aria-label="Download error report"
              >
                <Download className="w-4 h-4" />
              </button>
            )}
            {job.errorRows > 0 && (
              <button
                type="button"
                onClick={() => setOpen((o) => !o)}
                className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                aria-label={open ? "Hide errors" : "Show errors"}
              >
                {open ? (
                  <ChevronDown className="w-4 h-4" />
                ) : (
                  <ChevronRight className="w-4 h-4" />
                )}
              </button>
            )}
          </div>
        </td>
      </tr>
      {open && (
        <tr className="bg-surface">
          <td colSpan={6} className="px-4 pb-4">
            <ErrorRowsDrawer companyId={companyId} jobId={job.id} />
          </td>
        </tr>
      )}
    </>
  );
}

export default function RecentImportsTable({
  companyId,
}: {
  companyId: string;
}) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["imports", companyId, "list"],
    queryFn: () => importsApi.list(companyId, { size: 20 }).then((r) => r.data),
  });

  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-surface shadow-sm p-6">
        <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (isError || !data) {
    return (
      <div className="rounded-xl border border-border bg-surface shadow-sm p-6">
        <p className="text-danger text-sm">Failed to load recent imports.</p>
      </div>
    );
  }
  if (data.items.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-surface shadow-sm p-6">
        <p className="text-muted-foreground text-sm">
          No imports yet. Drop a CSV on the Import tab to start.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-border bg-surface shadow-sm overflow-hidden">
      <table className="w-full text-sm">
        <thead className="text-left text-xs font-semibold text-muted-foreground bg-surface">
          <tr>
            <th className="px-4 py-3">Job</th>
            <th className="px-4 py-3">Type</th>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">OK / Err / Total</th>
            <th className="px-4 py-3">Created</th>
            <th className="px-4 py-3 text-right">Actions</th>
          </tr>
        </thead>
        <tbody>
          {data.items.map((job) => (
            <ExpandableRow key={job.id} companyId={companyId} job={job} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
