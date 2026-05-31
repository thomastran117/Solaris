import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Loader2, Download, ChevronRight, ChevronDown } from "lucide-react";
import { importsApi } from "../../api/imports";
import type { ImportJob, ImportJobStatus } from "../../types/imports";

function StatusPill({ status }: { status: ImportJobStatus }) {
  const tone: Record<ImportJobStatus, string> = {
    PENDING: "bg-white/10 border-white/15 text-white/70",
    PARSING: "bg-sky-500/15 border-sky-400/30 text-sky-200",
    VALIDATING: "bg-sky-500/15 border-sky-400/30 text-sky-200",
    PROCESSING: "bg-sky-500/15 border-sky-400/30 text-sky-200",
    COMPLETED: "bg-green-500/15 border-green-400/30 text-green-300",
    COMPLETED_WITH_ERRORS: "bg-yellow-500/15 border-yellow-400/30 text-yellow-300",
    FAILED: "bg-red-500/15 border-red-400/30 text-red-300",
  };
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${tone[status]}`}>
      {status.replace(/_/g, " ")}
    </span>
  );
}

function ErrorRowsDrawer({ companyId, jobId }: { companyId: string; jobId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ["imports", companyId, jobId, "errors"],
    queryFn: () => importsApi.errors(companyId, jobId, { size: 50 }).then(r => r.data),
  });
  if (isLoading) {
    return <Loader2 className="w-4 h-4 animate-spin text-white/60" />;
  }
  if (!data || data.items.length === 0) {
    return <p className="text-xs text-white/60">No error rows recorded.</p>;
  }
  return (
    <div className="mt-2 max-h-60 overflow-y-auto rounded-xl border border-white/10 bg-white/[0.04]">
      <table className="w-full text-xs">
        <thead className="text-white/45 uppercase tracking-[0.18em]">
          <tr>
            <th className="px-3 py-2 text-left">Row</th>
            <th className="px-3 py-2 text-left">SKU</th>
            <th className="px-3 py-2 text-left">Error</th>
          </tr>
        </thead>
        <tbody>
          {data.items.map(r => (
            <tr key={r.id} className="border-t border-white/5">
              <td className="px-3 py-1.5 text-white/70">{r.rowNumber}</td>
              <td className="px-3 py-1.5 text-white/80">{r.sku ?? "—"}</td>
              <td className="px-3 py-1.5 text-red-300">{r.errorMessage}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ExpandableRow({ companyId, job }: { companyId: string; job: ImportJob }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <tr className="border-t border-white/5 hover:bg-white/[0.04] transition-colors">
        <td className="px-4 py-2.5 text-white/70">#{job.id}</td>
        <td className="px-4 py-2.5 text-white/80">{job.jobType.replace(/_/g, " ")}</td>
        <td className="px-4 py-2.5"><StatusPill status={job.status} /></td>
        <td className="px-4 py-2.5 text-white/80">
          {job.successRows} / {job.errorRows} / {job.totalRows}
        </td>
        <td className="px-4 py-2.5 text-white/60 text-xs">
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
                className="p-1.5 rounded-lg text-white/60 hover:text-sky-200 hover:bg-white/10 transition-colors"
                aria-label="Download error report"
              >
                <Download className="w-4 h-4" />
              </button>
            )}
            {job.errorRows > 0 && (
              <button
                type="button"
                onClick={() => setOpen(o => !o)}
                className="p-1.5 rounded-lg text-white/60 hover:text-white hover:bg-white/10 transition-colors"
                aria-label={open ? "Hide errors" : "Show errors"}
              >
                {open ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
              </button>
            )}
          </div>
        </td>
      </tr>
      {open && (
        <tr className="bg-white/[0.02]">
          <td colSpan={6} className="px-4 pb-4">
            <ErrorRowsDrawer companyId={companyId} jobId={job.id} />
          </td>
        </tr>
      )}
    </>
  );
}

export default function RecentImportsTable({ companyId }: { companyId: string }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["imports", companyId, "list"],
    queryFn: () => importsApi.list(companyId, { size: 20 }).then(r => r.data),
  });

  if (isLoading) {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
        <Loader2 className="w-5 h-5 animate-spin text-white/60" />
      </div>
    );
  }
  if (isError || !data) {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
        <p className="text-red-400 text-sm">Failed to load recent imports.</p>
      </div>
    );
  }
  if (data.items.length === 0) {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
        <p className="text-white/55 text-sm">No imports yet. Drop a CSV on the Import tab to start.</p>
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm overflow-hidden">
      <table className="w-full text-sm">
        <thead className="text-left text-xs uppercase tracking-[0.18em] font-semibold text-white/45 bg-white/[0.04]">
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
          {data.items.map(job => (
            <ExpandableRow key={job.id} companyId={companyId} job={job} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
