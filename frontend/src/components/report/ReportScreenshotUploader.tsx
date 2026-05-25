import { useRef, useState, type ChangeEvent } from "react";
import axios from "axios";
import { Image as ImageIcon, Loader2, X } from "lucide-react";
import { reportsApi } from "../../api/reports";

const MIME_BY_EXT: Record<string, string> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
};

const MAX_SCREENSHOTS = 5;
const MAX_FILE_BYTES = 5 * 1024 * 1024;

interface Props {
  urls: string[];
  onChange: (urls: string[]) => void;
}

export default function ReportScreenshotUploader({ urls, onChange }: Props) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const remaining = Math.max(0, MAX_SCREENSHOTS - urls.length);

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError(null);

    const accepted: File[] = [];
    for (const file of Array.from(files).slice(0, remaining)) {
      const ext = (file.name.split(".").pop() ?? "").toLowerCase();
      const contentType = MIME_BY_EXT[ext];
      if (!contentType) {
        setError(`Unsupported file type: ${file.name}. Use JPG, PNG, or WEBP.`);
        continue;
      }
      if (file.size > MAX_FILE_BYTES) {
        setError(`${file.name} exceeds 5 MB`);
        continue;
      }
      accepted.push(file);
    }
    if (accepted.length === 0) return;

    setUploading(true);
    const next = [...urls];
    try {
      for (const file of accepted) {
        const ext = (file.name.split(".").pop() ?? "").toLowerCase();
        const contentType = MIME_BY_EXT[ext];
        const presign = await reportsApi.presignScreenshot(contentType);
        await axios.put(presign.data.uploadUrl, file, {
          headers: { "Content-Type": contentType },
        });
        next.push(presign.data.fileUrl);
      }
      onChange(next);
    } catch {
      setError("Upload failed. Please try again.");
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  function handleRemove(url: string) {
    onChange(urls.filter((u) => u !== url));
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-3">
        {urls.map((url) => (
          <div
            key={url}
            className="relative h-20 w-20 overflow-hidden rounded-xl border border-white/10 bg-white/[0.04]"
          >
            <img src={url} alt="" className="h-full w-full object-cover" />
            <button
              type="button"
              onClick={() => handleRemove(url)}
              aria-label="Remove screenshot"
              className="absolute right-1 top-1 rounded-full bg-slate-950/80 p-1 text-white/80 hover:bg-slate-950 hover:text-white"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
        {remaining > 0 && (
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            disabled={uploading}
            className="flex h-20 w-20 flex-col items-center justify-center gap-1 rounded-xl border border-dashed border-white/20 bg-white/[0.04] text-xs text-white/60 hover:border-sky-400/40 hover:text-white disabled:opacity-60"
          >
            {uploading ? (
              <Loader2 className="h-4 w-4 animate-spin text-sky-200" />
            ) : (
              <ImageIcon className="h-4 w-4 text-sky-200" />
            )}
            <span>Add photo</span>
          </button>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        onChange={(e: ChangeEvent<HTMLInputElement>) => handleFiles(e.target.files)}
        className="hidden"
      />
      <p className="text-xs text-white/55">
        {urls.length}/{MAX_SCREENSHOTS} screenshots · max 5 MB each (JPG, PNG, WEBP)
      </p>
      {error && <p className="text-xs text-red-400">{error}</p>}
    </div>
  );
}
