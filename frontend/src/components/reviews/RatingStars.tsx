import { Star } from "lucide-react";

interface Props {
  rating: number;
  size?: "sm" | "md" | "lg";
  interactive?: boolean;
  onChange?: (rating: number) => void;
  label?: string;
}

const SIZE_CLASS: Record<NonNullable<Props["size"]>, string> = {
  sm: "h-3.5 w-3.5",
  md: "h-4 w-4",
  lg: "h-5 w-5",
};

export default function RatingStars({ rating, size = "md", interactive, onChange, label }: Props) {
  const stars = [1, 2, 3, 4, 5];
  const sizeClass = SIZE_CLASS[size];

  return (
    <div
      className="inline-flex items-center gap-0.5"
      role={interactive ? "radiogroup" : "img"}
      aria-label={label ?? `${rating} out of 5 stars`}
    >
      {stars.map((s) => {
        const filled = s <= Math.round(rating);
        const Icon = (
          <Star
            className={`${sizeClass} ${
              filled ? "fill-blue-400 text-blue-400" : "text-white/25"
            } transition`}
          />
        );
        if (!interactive) return <span key={s}>{Icon}</span>;
        return (
          <button
            key={s}
            type="button"
            role="radio"
            aria-checked={s === rating}
            onClick={() => onChange?.(s)}
            className="rounded p-0.5 hover:scale-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-400/60"
          >
            {Icon}
          </button>
        );
      })}
    </div>
  );
}
