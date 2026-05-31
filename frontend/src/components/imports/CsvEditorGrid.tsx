import { useMemo } from "react";
import type { ImportJobType } from "../../types/imports";

export const CSV_COLUMNS = [
  "sku",
  "name",
  "description",
  "price",
  "compareAtPrice",
  "currency",
  "category",
  "brand",
  "tags",
  "stock",
  "weight",
  "weightUnit",
  "featured",
  "purchasable",
  "listed",
  "status",
  "thumbnailUrl",
  "imageUrl1",
  "imageUrl2",
  "imageUrl3",
  "imageUrl4",
  "imageUrl5",
] as const;

export type CsvColumn = (typeof CSV_COLUMNS)[number];
export type CsvRow = Partial<Record<CsvColumn, string>>;

const TRUTHY = new Set(["true", "yes", "y", "1", "on"]);
const FALSY = new Set(["false", "no", "n", "0", "off"]);
const STATUSES = new Set(["DRAFT", "SCHEDULED", "ACTIVE", "ARCHIVED"]);

function isBlank(v: string | undefined): boolean {
  return v == null || v.trim() === "";
}

/**
 * Mirrors the backend validation in {@code ImportServiceImpl.validatePerJobType}.
 * Catching errors here lets admins fix them before a server round-trip.
 */
export function validateRow(row: CsvRow, jobType: ImportJobType): Record<string, string> {
  const errors: Record<string, string> = {};
  if (isBlank(row.sku)) {
    errors.sku = "SKU is required";
  }
  const stockRaw = row.stock?.trim();

  if (jobType === "INVENTORY_SYNC") {
    if (isBlank(stockRaw)) {
      errors.stock = "stock is required";
    } else if (!/^\d+$/.test(stockRaw!)) {
      errors.stock = "must be a non-negative integer";
    }
    return errors;
  }

  if (isBlank(row.name)) errors.name = "name is required";
  const priceRaw = row.price?.trim();
  if (isBlank(priceRaw)) {
    errors.price = "price is required";
  } else if (!/^\d+(\.\d{1,2})?$/.test(priceRaw!)) {
    errors.price = "must be a decimal (e.g. 19.99)";
  }
  if (!isBlank(stockRaw) && !/^\d+$/.test(stockRaw!)) {
    errors.stock = "must be a non-negative integer";
  }
  if (!isBlank(row.compareAtPrice) && !/^\d+(\.\d{1,2})?$/.test(row.compareAtPrice!.trim())) {
    errors.compareAtPrice = "must be a decimal";
  }
  if (!isBlank(row.currency) && !/^[A-Za-z]{3}$/.test(row.currency!.trim())) {
    errors.currency = "3-letter ISO code";
  }
  if (!isBlank(row.status) && !STATUSES.has(row.status!.trim().toUpperCase())) {
    errors.status = "DRAFT / SCHEDULED / ACTIVE / ARCHIVED";
  }
  for (const col of ["featured", "purchasable", "listed"] as const) {
    const raw = row[col]?.trim().toLowerCase();
    if (raw && !TRUTHY.has(raw) && !FALSY.has(raw)) {
      errors[col] = "true/false/yes/no/1/0";
    }
  }
  return errors;
}

interface Props {
  rows: CsvRow[];
  onChange: (rows: CsvRow[]) => void;
  jobType: ImportJobType;
}

export default function CsvEditorGrid({ rows, onChange, jobType }: Props) {
  const columns: readonly CsvColumn[] = useMemo(() => {
    return jobType === "INVENTORY_SYNC"
      ? (["sku", "stock"] as const)
      : CSV_COLUMNS;
  }, [jobType]);

  const rowErrors = useMemo(
    () => rows.map(r => validateRow(r, jobType)),
    [rows, jobType],
  );

  const totalErrors = rowErrors.reduce((n, e) => n + Object.keys(e).length, 0);

  function updateCell(rowIndex: number, col: CsvColumn, value: string) {
    const next = rows.slice();
    next[rowIndex] = { ...next[rowIndex], [col]: value };
    onChange(next);
  }

  function removeRow(rowIndex: number) {
    onChange(rows.filter((_, i) => i !== rowIndex));
  }

  function addRow() {
    onChange([...rows, {}]);
  }

  if (rows.length === 0) {
    return (
      <p className="text-sm text-white/55 px-4 py-8 text-center">
        No rows to review yet — drop a CSV above to begin.
      </p>
    );
  }

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.04] backdrop-blur shadow-sm overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
        <div className="text-xs text-white/60">
          {rows.length} rows · <span className={totalErrors > 0 ? "text-red-300" : "text-green-300"}>
            {totalErrors} issue{totalErrors === 1 ? "" : "s"}
          </span>
        </div>
        <button
          type="button"
          onClick={addRow}
          className="px-3 py-1.5 rounded-full text-xs font-semibold border border-white/15 text-white/80 hover:bg-white/10 transition-colors"
        >
          + Row
        </button>
      </div>
      <div className="max-h-[500px] overflow-auto">
        <table className="w-full text-xs">
          <thead className="sticky top-0 bg-slate-900/95 backdrop-blur text-white/45 uppercase tracking-[0.16em]">
            <tr>
              <th className="px-2 py-2 text-left w-10">#</th>
              {columns.map(c => (
                <th key={c} className="px-2 py-2 text-left whitespace-nowrap">{c}</th>
              ))}
              <th className="px-2 py-2 w-10" />
            </tr>
          </thead>
          <tbody>
            {rows.map((row, ri) => {
              const errs = rowErrors[ri];
              return (
                <tr key={ri} className="border-t border-white/5 align-top">
                  <td className="px-2 py-1.5 text-white/45">{ri + 1}</td>
                  {columns.map(c => {
                    const err = errs[c];
                    return (
                      <td key={c} className="px-1 py-1">
                        <input
                          type="text"
                          value={row[c] ?? ""}
                          onChange={e => updateCell(ri, c, e.target.value)}
                          title={err}
                          className={[
                            "w-full px-2 py-1 rounded text-white text-xs bg-transparent border placeholder:text-white/30 focus:outline-none focus:ring-1",
                            err
                              ? "border-red-400/60 bg-red-500/10 focus:ring-red-400/60"
                              : "border-white/10 hover:border-white/20 focus:ring-sky-400/40",
                          ].join(" ")}
                          style={{ minWidth: 100 }}
                        />
                        {err && (
                          <p className="text-[10px] text-red-300 mt-0.5 leading-tight">{err}</p>
                        )}
                      </td>
                    );
                  })}
                  <td className="px-1 py-1 text-right">
                    <button
                      type="button"
                      onClick={() => removeRow(ri)}
                      className="text-white/40 hover:text-red-300 text-base leading-none"
                      aria-label="Remove row"
                    >
                      ×
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
