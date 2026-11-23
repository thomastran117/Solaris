import { useRef, useState, type ChangeEvent } from "react";
import axios from "axios";
import { Image as ImageIcon, Loader2, X } from "lucide-react";
import { reviewsApi } from "../../api/reviews";
import type { ReviewMedia } from "../../types/review";

const MIME_BY_EXT: Record<string, string> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
  gif: "image/gif",
};

const MAX_PHOTOS = 5;
const MAX_FILE_BYTES = 5 * 1024 * 1024;

interface Props {
  companyId: string;
  productId: string;
  reviewId: string;
  media: ReviewMedia[];
  onChange: (media: ReviewMedia[]) => void;
}

export default function ReviewPhotoUploader({
  companyId,
  productId,
  reviewId,
  media,
  onChange,
}: Props) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const remaining = Math.max(0, MAX_PHOTOS - media.length);

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError(null);

    const accepted: File[] = [];
    for (const file of Array.from(files).slice(0, remaining)) {
      const ext = (file.name.split(".").pop() ?? "").toLowerCase();
      const contentType = MIME_BY_EXT[ext];
      if (!contentType) {
        setError(`Unsupported file type: ${file.name}`);
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
    const next: ReviewMedia[] = [...media];
    try {
      for (const file of accepted) {
        const ext = (file.name.split(".").pop() ?? "").toLowerCase();
        const contentType = MIME_BY_EXT[ext];
        const presign = await reviewsApi.presignMediaUpload(contentType);
        await axios.put(presign.data.uploadUrl, file, {
          headers: { "Content-Type": contentType },
        });
        const attached = await reviewsApi.attachMedia(
          companyId,
          productId,
          reviewId,
          presign.data.fileUrl
        );
        next.push(attached.data);
      }
      onChange(next);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Upload failed";
      setError(msg);
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  async function handleRemove(mediaId: string) {
    setError(null);
    try {
      await reviewsApi.deleteMedia(companyId, productId, reviewId, mediaId);
      onChange(media.filter((m) => m.id !== mediaId));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Delete failed";
      setError(msg);
    }
  }

  const onInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    handleFiles(e.target.files);
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-3">
        {media.map((m) => (
          <div
            key={m.id}
            className="relative h-20 w-20 overflow-hidden rounded-xl border border-border bg-surface"
          >
            <img src={m.url} alt="" className="h-full w-full object-cover" />
            <button
              type="button"
              onClick={() => handleRemove(m.id)}
              aria-label="Remove photo"
              className="absolute right-1 top-1 rounded-full bg-background p-1 text-foreground hover:text-foreground hover:bg-background"
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
            className="flex h-20 w-20 flex-col items-center justify-center gap-1 rounded-xl border border-dashed border-input bg-surface text-xs text-muted-foreground hover:border-primary/40 hover:text-foreground disabled:opacity-60"
          >
            {uploading ? (
              <Loader2 className="h-4 w-4 animate-spin text-link" />
            ) : (
              <ImageIcon className="h-4 w-4 text-link" />
            )}
            <span>Add photo</span>
          </button>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        multiple
        onChange={onInputChange}
        className="hidden"
      />
      <p className="text-xs text-muted-foreground">
        {media.length}/{MAX_PHOTOS} photos · max 5 MB each (JPG, PNG, WEBP, GIF)
      </p>
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  );
}
