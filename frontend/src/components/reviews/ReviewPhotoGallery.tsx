import { useEffect, useState } from "react";
import { X } from "lucide-react";
import type { ReviewMedia } from "../../types/review";

interface Props {
  media: ReviewMedia[];
}

export default function ReviewPhotoGallery({ media }: Props) {
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  useEffect(() => {
    if (activeIndex === null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setActiveIndex(null);
      if (e.key === "ArrowRight")
        setActiveIndex((i) => (i === null ? null : (i + 1) % media.length));
      if (e.key === "ArrowLeft")
        setActiveIndex((i) =>
          i === null ? null : (i - 1 + media.length) % media.length
        );
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [activeIndex, media.length]);

  if (media.length === 0) return null;

  return (
    <>
      <div className="flex flex-wrap gap-2">
        {media.map((m, i) => (
          <button
            key={m.id}
            type="button"
            onClick={() => setActiveIndex(i)}
            className="h-16 w-16 overflow-hidden rounded-lg border border-border bg-surface hover:border-input"
            aria-label="View photo"
          >
            <img src={m.url} alt="" className="h-full w-full object-cover" />
          </button>
        ))}
      </div>

      {activeIndex !== null && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-overlay p-6"
          onClick={() => setActiveIndex(null)}
          role="dialog"
          aria-modal="true"
        >
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              setActiveIndex(null);
            }}
            aria-label="Close"
            className="absolute right-4 top-4 rounded-full border border-border bg-surface p-2 text-foreground hover:text-foreground hover:border-input"
          >
            <X className="h-5 w-5" />
          </button>
          <img
            src={media[activeIndex].url}
            alt=""
            onClick={(e) => e.stopPropagation()}
            className="max-h-[85vh] max-w-[90vw] rounded-xl border border-border object-contain"
          />
        </div>
      )}
    </>
  );
}
