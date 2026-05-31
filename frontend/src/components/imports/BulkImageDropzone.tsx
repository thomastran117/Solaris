import { useCallback, useRef, useState } from "react";
import axios from "axios";
import { Image as ImageIcon, Upload, CheckCircle2, AlertCircle, Loader2 } from "lucide-react";
import { importsApi } from "../../api/imports";

const MIME_BY_EXT: Record<string, string> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
  gif: "image/gif",
};

type ItemStatus = "pending" | "uploading" | "uploaded" | "failed";

interface Item {
  id: string;
  file: File;
  sku: string;
  displayOrder: number;
  status: ItemStatus;
  imageUrl: string | null;
  error: string | null;
}

const CONCURRENCY = 6;

/**
 * Parses {SKU}.ext or {SKU}-{n}.ext (e.g. ABC-001-2.png → sku=ABC-001, order=1).
 * Position defaults to 0 (first slot).
 */
function parseFilename(name: string): { sku: string; displayOrder: number } {
  const dot = name.lastIndexOf(".");
  const base = dot > 0 ? name.substring(0, dot) : name;
  const m = base.match(/^(.+?)-(\d+)$/);
  if (m) {
    const n = parseInt(m[2], 10);
    return { sku: m[1], displayOrder: Math.max(0, n - 1) };
  }
  return { sku: base, displayOrder: 0 };
}

interface Props {
  companyId: string;
}

export default function BulkImageDropzone({ companyId }: Props) {
  const [items, setItems] = useState<Item[]>([]);
  const [busy, setBusy] = useState(false);
  const [attaching, setAttaching] = useState(false);
  const [attachResult, setAttachResult] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const onFiles = useCallback((files: FileList | File[]) => {
    const next: Item[] = [];
    for (const file of Array.from(files)) {
      const ext = (file.name.split(".").pop() ?? "").toLowerCase();
      if (!MIME_BY_EXT[ext]) continue;
      const { sku, displayOrder } = parseFilename(file.name);
      next.push({
        id: `${file.name}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        file,
        sku,
        displayOrder,
        status: "pending",
        imageUrl: null,
        error: null,
      });
    }
    setItems(prev => [...prev, ...next]);
  }, []);

  const onDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      e.preventDefault();
      onFiles(e.dataTransfer.files);
    },
    [onFiles],
  );

  function setItem(id: string, patch: Partial<Item>) {
    setItems(prev => prev.map(it => (it.id === id ? { ...it, ...patch } : it)));
  }

  async function uploadOne(item: Item): Promise<Item> {
    const ext = (item.file.name.split(".").pop() ?? "").toLowerCase();
    const contentType = MIME_BY_EXT[ext] ?? "application/octet-stream";
    setItem(item.id, { status: "uploading" });
    try {
      const r = await importsApi.presignImage(contentType);
      await axios.put(r.data.uploadUrl, item.file, {
        headers: { "Content-Type": contentType },
      });
      const next: Partial<Item> = { status: "uploaded", imageUrl: r.data.fileUrl };
      setItem(item.id, next);
      return { ...item, ...next };
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Upload failed";
      setItem(item.id, { status: "failed", error: msg });
      return { ...item, status: "failed", error: msg };
    }
  }

  async function uploadAll() {
    setBusy(true);
    setAttachResult(null);
    const queue = items.filter(it => it.status === "pending" || it.status === "failed");
    let cursor = 0;
    async function worker() {
      while (cursor < queue.length) {
        const idx = cursor++;
        await uploadOne(queue[idx]);
      }
    }
    await Promise.all(Array.from({ length: CONCURRENCY }, () => worker()));
    setBusy(false);
  }

  async function attachAll() {
    setAttaching(true);
    setAttachResult(null);
    const ready = items.filter(it => it.status === "uploaded" && it.imageUrl);
    if (ready.length === 0) {
      setAttaching(false);
      setAttachResult("No uploaded images to attach yet.");
      return;
    }
    try {
      const r = await importsApi.attachImages(
        companyId,
        ready.map(it => ({
          sku: it.sku,
          imageUrl: it.imageUrl as string,
          displayOrder: it.displayOrder,
        })),
      );
      setAttachResult(`Attached ${r.data.attached} of ${ready.length} images.`);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Attach failed";
      setAttachResult(`Attach failed: ${msg}`);
    } finally {
      setAttaching(false);
    }
  }

  const total = items.length;
  const uploaded = items.filter(i => i.status === "uploaded").length;
  const failed = items.filter(i => i.status === "failed").length;

  return (
    <div className="space-y-4">
      <div
        onDrop={onDrop}
        onDragOver={e => e.preventDefault()}
        className="rounded-2xl border-2 border-dashed border-white/15 bg-white/[0.04] backdrop-blur p-10 text-center hover:border-sky-400/40 transition-colors"
      >
        <ImageIcon className="w-8 h-8 text-sky-200 mx-auto mb-3" />
        <p className="text-sm text-white/80 font-medium mb-1">
          Drop product images here, or click to choose files
        </p>
        <p className="text-xs text-white/55">
          Filename convention: <code className="text-sky-200">SKU.jpg</code> or
          <code className="text-sky-200"> SKU-2.jpg</code> for additional positions
        </p>
        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          multiple
          className="hidden"
          onChange={e => {
            if (e.target.files) onFiles(e.target.files);
            e.target.value = "";
          }}
        />
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="mt-4 inline-flex items-center gap-2 px-4 py-2 rounded-full bg-blue-600 hover:bg-blue-500 text-sm font-semibold text-white transition-colors"
        >
          <Upload className="w-4 h-4" />
          Choose images
        </button>
      </div>

      {total > 0 && (
        <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
            <div className="text-xs text-white/65">
              {total} files · {uploaded} uploaded · <span className={failed > 0 ? "text-red-300" : "text-white/65"}>{failed} failed</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={uploadAll}
                disabled={busy}
                className="px-3 py-1.5 rounded-full text-xs font-semibold border border-white/15 text-white/80 hover:bg-white/10 transition-colors disabled:opacity-40"
              >
                {busy ? "Uploading…" : "Upload all"}
              </button>
              <button
                type="button"
                onClick={attachAll}
                disabled={attaching || uploaded === 0}
                className="px-3 py-1.5 rounded-full text-xs font-semibold bg-blue-600 hover:bg-blue-500 text-white transition-colors disabled:opacity-40"
              >
                {attaching ? "Attaching…" : "Attach to products"}
              </button>
            </div>
          </div>
          <div className="max-h-72 overflow-auto">
            <table className="w-full text-xs">
              <thead className="text-left text-white/45 uppercase tracking-[0.16em] bg-white/[0.04]">
                <tr>
                  <th className="px-3 py-2">File</th>
                  <th className="px-3 py-2">SKU</th>
                  <th className="px-3 py-2">Position</th>
                  <th className="px-3 py-2">Status</th>
                </tr>
              </thead>
              <tbody>
                {items.map(it => (
                  <tr key={it.id} className="border-t border-white/5">
                    <td className="px-3 py-1.5 text-white/80 truncate max-w-[260px]">{it.file.name}</td>
                    <td className="px-3 py-1.5">
                      <input
                        value={it.sku}
                        onChange={e => setItem(it.id, { sku: e.target.value })}
                        className="px-2 py-0.5 rounded text-white text-xs bg-transparent border border-white/10 hover:border-white/20 focus:outline-none focus:ring-1 focus:ring-sky-400/40"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      <input
                        type="number"
                        min={0}
                        value={it.displayOrder}
                        onChange={e => setItem(it.id, { displayOrder: parseInt(e.target.value, 10) || 0 })}
                        className="w-14 px-2 py-0.5 rounded text-white text-xs bg-transparent border border-white/10 hover:border-white/20 focus:outline-none focus:ring-1 focus:ring-sky-400/40"
                      />
                    </td>
                    <td className="px-3 py-1.5">
                      {it.status === "pending" && <span className="text-white/55">pending</span>}
                      {it.status === "uploading" && (
                        <span className="inline-flex items-center gap-1 text-sky-200">
                          <Loader2 className="w-3.5 h-3.5 animate-spin" /> uploading
                        </span>
                      )}
                      {it.status === "uploaded" && (
                        <span className="inline-flex items-center gap-1 text-green-300">
                          <CheckCircle2 className="w-3.5 h-3.5" /> uploaded
                        </span>
                      )}
                      {it.status === "failed" && (
                        <span className="inline-flex items-center gap-1 text-red-300" title={it.error ?? ""}>
                          <AlertCircle className="w-3.5 h-3.5" /> failed
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {attachResult && (
            <p className="px-4 py-2 text-xs text-sky-200 border-t border-white/10">{attachResult}</p>
          )}
        </div>
      )}
    </div>
  );
}
