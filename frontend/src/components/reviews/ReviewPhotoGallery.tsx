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
      if (e.key === "ArrowRight") setActiveIndex((i) => (i === null ? null : (i + 1) % media.length));
      if (e.key === "ArrowLeft")
        setActiveIndex((i) => (i === null ? null : (i - 1 + media.length) % media.length));
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
            className="h-16 w-16 overflow-hidden rounded-lg border border-white/10 bg-white/[0.04] hover:border-white/30"
            aria-label="View photo"
          >
            <img src={m.url} alt="" className="h-full w-full object-cover" />
          </button>
        ))}
      </div>

      {activeIndex !== null && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/85 backdrop-blur p-6"
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
            className="absolute right-4 top-4 rounded-full border border-white/15 bg-white/[0.06] p-2 text-white/80 hover:text-white hover:border-white/30"
          >
            <X className="h-5 w-5" />
          </button>
          <img
            src={media[activeIndex].url}
            alt=""
            onClick={(e) => e.stopPropagation()}
            className="max-h-[85vh] max-w-[90vw] rounded-2xl border border-white/10 object-contain"
          />
        </div>
      )}
    </>
  );
}
