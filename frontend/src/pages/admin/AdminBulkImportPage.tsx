import { useRef, useState } from "react";
import axios from "axios";
import { useSelector } from "react-redux";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { FileSpreadsheet, Upload, Download, Image as ImageIcon, ArrowRight, RefreshCw } from "lucide-react";
import Papa from "papaparse";
import type { RootState } from "../../stores";
import { importsApi } from "../../api/imports";
import type { ImportJob, ImportJobType, ImportMode } from "../../types/imports";
import CsvEditorGrid, { CSV_COLUMNS, type CsvColumn, type CsvRow, validateRow } from "../../components/imports/CsvEditorGrid";
import ImportJobProgress from "../../components/imports/ImportJobProgress";
import BulkImageDropzone from "../../components/imports/BulkImageDropzone";
import RecentImportsTable from "../../components/imports/RecentImportsTable";

type Tab = "import" | "images" | "export";

const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();
  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.35 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.55 } } };
  const stagger: Variants = prefersReducedMotion
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.04 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.08 } } };
  return { fadeInUp, stagger };
};

function isCsvColumn(s: string): s is CsvColumn {
  return (CSV_COLUMNS as readonly string[]).includes(s);
}

function normaliseParsedRows(raw: Record<string, string>[]): CsvRow[] {
  return raw.map(row => {
    const next: CsvRow = {};
    for (const key of Object.keys(row)) {
      const lower = key.trim();
      const match = CSV_COLUMNS.find(c => c.toLowerCase() === lower.toLowerCase());
      if (match && isCsvColumn(match)) {
        next[match] = row[key];
      }
    }
    return next;
  });
}

function rowsToCsv(rows: CsvRow[]): string {
  const objects = rows.map(r => {
    const o: Record<string, string> = {};
    for (const c of CSV_COLUMNS) o[c] = r[c] ?? "";
    return o;
  });
  return Papa.unparse(objects, { columns: [...CSV_COLUMNS] });
}

export default function AdminBulkImportPage() {
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();

  const [tab, setTab] = useState<Tab>("import");
  const [jobType, setJobType] = useState<ImportJobType>("PRODUCT_UPSERT");
  const [mode, setMode] = useState<ImportMode>("UPSERT");
  const [rows, setRows] = useState<CsvRow[]>([]);
  const [fileName, setFileName] = useState<string | null>(null);
  const [parsing, setParsing] = useState(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const [activeJobId, setActiveJobId] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const totalErrors = rows.reduce((n, r) => n + Object.keys(validateRow(r, jobType)).length, 0);

  function handleFile(file: File) {
    setParseError(null);
    setParsing(true);
    setFileName(file.name);
    Papa.parse<Record<string, string>>(file, {
      header: true,
      skipEmptyLines: true,
      complete: results => {
        if (results.errors.length > 0) {
          const first = results.errors[0];
          setParseError(`CSV parse: ${first.message} (row ${first.row ?? "?"})`);
        }
        setRows(normaliseParsedRows(results.data));
        setParsing(false);
      },
      error: err => {
        setParseError(err.message);
        setParsing(false);
      },
    });
  }

  const startImport = useMutation({
    mutationFn: async () => {
      if (!companyId) throw new Error("No company in scope");
      // Re-serialise the (possibly edited) grid back to a CSV and upload to S3
      const csv = rowsToCsv(rows);
      const presign = await importsApi.presignCsv("text/csv");
      await axios.put(presign.data.uploadUrl, csv, {
        headers: { "Content-Type": "text/csv" },
      });
      const job = await importsApi.create(companyId, {
        jobType,
        mode: jobType === "PRODUCT_UPSERT" ? mode : undefined,
        csvS3Key: presign.data.key,
        fileName: fileName ?? undefined,
      });
      return job.data;
    },
    onSuccess: (job: ImportJob) => {
      setActiveJobId(job.id);
      queryClient.invalidateQueries({ queryKey: ["imports", companyId, "list"] });
    },
  });

  const exportMutation = useMutation({
    mutationFn: async () => {
      if (!companyId) throw new Error("No company in scope");
      return importsApi.export(companyId).then(r => r.data);
    },
    onSuccess: download => {
      window.open(download.url, "_blank");
    },
  });

  if (!companyId) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center px-6">
        <p className="text-white/60 text-sm">Sign in with a vendor account to bulk-edit products.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 relative overflow-hidden">
      <div aria-hidden className="pointer-events-none fixed inset-0" style={{ zIndex: 0 }}>
        <div className="absolute top-1/4 left-1/4 w-[600px] h-[600px] rounded-full bg-blue-600/10 blur-[120px]" />
        <div className="absolute bottom-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-sky-400/8 blur-[100px]" />
      </div>

      <motion.div
        variants={stagger}
        initial="hidden"
        animate="visible"
        className="relative z-10 max-w-6xl mx-auto px-4 sm:px-6 py-10"
      >
        <motion.header variants={fadeInUp} className="mb-6">
          <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90 mb-2">
            Admin · Catalogue
          </p>
          <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-white">
            Bulk Import &amp; Export
          </h1>
          <p className="text-sm text-white/60 mt-1">
            Round-trip your catalogue through a spreadsheet, sync stock, and attach product images in bulk.
          </p>
        </motion.header>

        <motion.div variants={fadeInUp} className="flex flex-wrap gap-2 mb-6">
          {([
            { value: "import", label: "Import CSV", icon: FileSpreadsheet },
            { value: "images", label: "Bulk Images", icon: ImageIcon },
            { value: "export", label: "Export & History", icon: Download },
          ] as const).map(t => {
            const active = tab === t.value;
            const Icon = t.icon;
            return (
              <button
                key={t.value}
                type="button"
                onClick={() => setTab(t.value)}
                className={[
                  "inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold border transition-colors",
                  active
                    ? "border-sky-400/50 bg-sky-400/15 text-sky-100"
                    : "border-white/10 bg-white/[0.04] text-white/70 hover:bg-white/10",
                ].join(" ")}
              >
                <Icon className="w-4 h-4" />
                {t.label}
              </button>
            );
          })}
        </motion.div>

        {tab === "import" && (
          <motion.div variants={fadeInUp} className="space-y-6">
            <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
              <h2 className="text-lg font-semibold text-white mb-1">1. Pick mode</h2>
              <p className="text-xs text-white/60 mb-4">
                Match by SKU. Choose how the worker treats existing vs. new SKUs.
              </p>
              <div className="grid sm:grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => setJobType("PRODUCT_UPSERT")}
                  className={[
                    "text-left rounded-xl border p-4 transition-colors",
                    jobType === "PRODUCT_UPSERT"
                      ? "border-sky-400/50 bg-sky-400/10"
                      : "border-white/10 bg-white/[0.04] hover:bg-white/10",
                  ].join(" ")}
                >
                  <p className="text-sm font-semibold text-white">Products (upsert)</p>
                  <p className="text-xs text-white/60 mt-1">
                    Full product fields. New SKUs are created, existing SKUs are updated in place.
                  </p>
                </button>
                <button
                  type="button"
                  onClick={() => setJobType("INVENTORY_SYNC")}
                  className={[
                    "text-left rounded-xl border p-4 transition-colors",
                    jobType === "INVENTORY_SYNC"
                      ? "border-sky-400/50 bg-sky-400/10"
                      : "border-white/10 bg-white/[0.04] hover:bg-white/10",
                  ].join(" ")}
                >
                  <p className="text-sm font-semibold text-white">Inventory sync (stock only)</p>
                  <p className="text-xs text-white/60 mt-1">
                    Two columns: <code className="text-sky-200">sku, stock</code>. Generates
                    inventory adjustments with the difference from current stock.
                  </p>
                </button>
              </div>
              {jobType === "PRODUCT_UPSERT" && (
                <div className="flex flex-wrap gap-2 mt-4">
                  {(["UPSERT", "CREATE_ONLY", "UPDATE_ONLY"] as const).map(m => (
                    <button
                      key={m}
                      type="button"
                      onClick={() => setMode(m)}
                      className={[
                        "px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors",
                        mode === m
                          ? "border-sky-400/50 bg-sky-400/15 text-sky-100"
                          : "border-white/10 bg-white/[0.04] text-white/65 hover:bg-white/10",
                      ].join(" ")}
                    >
                      {m.replace(/_/g, " ")}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
              <h2 className="text-lg font-semibold text-white mb-1">2. Drop CSV</h2>
              <p className="text-xs text-white/60 mb-4">
                We parse the file in your browser so you can fix errors before any data hits the server.
              </p>
              <div
                onDrop={e => {
                  e.preventDefault();
                  const f = e.dataTransfer.files?.[0];
                  if (f) handleFile(f);
                }}
                onDragOver={e => e.preventDefault()}
                className="rounded-2xl border-2 border-dashed border-white/15 bg-white/[0.04] backdrop-blur p-8 text-center hover:border-sky-400/40 transition-colors"
              >
                <Upload className="w-7 h-7 text-sky-200 mx-auto mb-2" />
                <p className="text-sm text-white/80 font-medium mb-2">
                  {fileName ? `Loaded: ${fileName}` : "Drop a CSV here, or choose a file"}
                </p>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".csv,text/csv"
                  className="hidden"
                  onChange={e => {
                    const f = e.target.files?.[0];
                    if (f) handleFile(f);
                    e.target.value = "";
                  }}
                />
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="px-4 py-2 rounded-full text-sm font-semibold border border-white/20 text-white hover:bg-white/10 transition-colors"
                >
                  Choose CSV
                </button>
              </div>
              {parsing && <p className="mt-3 text-xs text-sky-200">Parsing…</p>}
              {parseError && <p className="mt-3 text-xs text-red-300">{parseError}</p>}
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
              <h2 className="text-lg font-semibold text-white mb-1">3. Review &amp; edit</h2>
              <p className="text-xs text-white/60 mb-4">
                Cells with validation errors are highlighted. Fix them in place — the corrected
                rows are what gets sent to the server.
              </p>
              <CsvEditorGrid rows={rows} onChange={setRows} jobType={jobType} />
            </div>

            <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
              <div className="flex items-center justify-between gap-4 flex-wrap">
                <div>
                  <h2 className="text-lg font-semibold text-white">4. Start import</h2>
                  <p className="text-xs text-white/60 mt-1">
                    {totalErrors > 0
                      ? `Resolve ${totalErrors} validation issue${totalErrors === 1 ? "" : "s"} or proceed anyway — failed rows will appear in the error report.`
                      : "All rows pass client-side validation."}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => startImport.mutate()}
                  disabled={rows.length === 0 || startImport.isPending}
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-blue-600 hover:bg-blue-500 text-sm font-semibold text-white transition-colors disabled:opacity-40"
                >
                  <ArrowRight className="w-4 h-4" />
                  {startImport.isPending ? "Starting…" : "Start import"}
                </button>
              </div>
              {startImport.isError && (
                <p className="text-xs text-red-300 mt-3">
                  {(startImport.error as Error).message || "Import failed to start."}
                </p>
              )}
            </div>

            {activeJobId !== null && (
              <ImportJobProgress
                companyId={companyId}
                jobId={activeJobId}
                onTerminal={() => {
                  // After terminal: reset the active job so re-uploading a fresh CSV starts clean.
                }}
              />
            )}
          </motion.div>
        )}

        {tab === "images" && (
          <motion.div variants={fadeInUp}>
            <BulkImageDropzone companyId={companyId} />
          </motion.div>
        )}

        {tab === "export" && (
          <motion.div variants={fadeInUp} className="space-y-6">
            <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm p-6">
              <h2 className="text-lg font-semibold text-white mb-1">Export catalogue</h2>
              <p className="text-xs text-white/60 mb-4">
                Download every product as a CSV. Edit it in Excel or Sheets, then drop the file back
                in the Import tab — matching SKUs are updated, new SKUs are created.
              </p>
              <button
                type="button"
                onClick={() => exportMutation.mutate()}
                disabled={exportMutation.isPending}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-blue-600 hover:bg-blue-500 text-sm font-semibold text-white transition-colors disabled:opacity-40"
              >
                {exportMutation.isPending ? (
                  <RefreshCw className="w-4 h-4 animate-spin" />
                ) : (
                  <Download className="w-4 h-4" />
                )}
                {exportMutation.isPending ? "Preparing…" : "Export CSV"}
              </button>
              {exportMutation.isError && (
                <p className="text-xs text-red-300 mt-3">
                  {(exportMutation.error as Error).message || "Export failed."}
                </p>
              )}
            </div>

            <div>
              <h2 className="text-lg font-semibold text-white mb-3">Recent jobs</h2>
              <RecentImportsTable companyId={companyId} />
            </div>
          </motion.div>
        )}
      </motion.div>
    </div>
  );
}
