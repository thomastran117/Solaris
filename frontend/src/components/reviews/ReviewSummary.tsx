import RatingStars from "./RatingStars";
import type { ReviewSummary as ReviewSummaryT } from "../../types/review";

interface Props {
  summary: ReviewSummaryT | undefined;
  isLoading?: boolean;
}

const STARS = [5, 4, 3, 2, 1] as const;

export default function ReviewSummary({ summary, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-6 backdrop-blur shadow-sm">
        <p className="text-sm text-white/60">Loading summary...</p>
      </div>
    );
  }
  if (!summary) return null;

  const total = summary.total;
  const avg = summary.averageRating || 0;

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.06] p-6 backdrop-blur shadow-sm">
      <div className="grid gap-6 md:grid-cols-[auto,1fr] md:items-center">
        <div className="flex flex-col items-start gap-2">
          <span className="text-4xl font-extrabold text-white">{avg.toFixed(1)}</span>
          <RatingStars rating={avg} size="lg" />
          <span className="text-xs text-white/60">
            {total} review{total === 1 ? "" : "s"} ·{" "}
            {summary.verifiedCount} verified
          </span>
        </div>
        <div className="space-y-2">
          {STARS.map((s) => {
            const count = summary.distribution[String(s)] ?? 0;
            const pct = total > 0 ? (count / total) * 100 : 0;
            return (
              <div key={s} className="flex items-center gap-3 text-xs">
                <span className="w-8 shrink-0 text-white/65">{s} star</span>
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-white/[0.06]">
                  <div
                    className="h-full bg-gradient-to-r from-sky-300 to-blue-500 transition-[width] duration-300"
                    style={{ width: `${pct}%` }}
                  />
                </div>
                <span className="w-10 shrink-0 text-right text-white/55">{count}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
