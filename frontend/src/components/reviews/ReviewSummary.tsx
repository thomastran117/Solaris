import RatingStars from "./RatingStars";
import type { ReviewSummary as ReviewSummaryT } from "../../types/review";
import { Progress } from "../ui/Progress";

interface Props {
  summary: ReviewSummaryT | undefined;
  isLoading?: boolean;
}

const STARS = [5, 4, 3, 2, 1] as const;

export default function ReviewSummary({ summary, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
        <p className="text-sm text-muted-foreground">Loading summary...</p>
      </div>
    );
  }
  if (!summary) return null;

  const total = summary.total;
  const avg = summary.averageRating || 0;

  return (
    <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
      <div className="grid gap-6 md:grid-cols-[auto,1fr] md:items-center">
        <div className="flex flex-col items-start gap-2">
          <span className="text-4xl font-semibold text-foreground">
            {avg.toFixed(1)}
          </span>
          <RatingStars rating={avg} size="lg" />
          <span className="text-xs text-muted-foreground">
            {total} review{total === 1 ? "" : "s"} · {summary.verifiedCount}{" "}
            verified
          </span>
        </div>
        <div className="space-y-2">
          {STARS.map((s) => {
            const count = summary.distribution[String(s)] ?? 0;
            const pct = total > 0 ? (count / total) * 100 : 0;
            return (
              <div key={s} className="flex items-center gap-3 text-xs">
                <span className="w-8 shrink-0 text-muted-foreground">
                  {s} star
                </span>
                <Progress
                  value={pct}
                  label={`${s}-star reviews`}
                  valueText={`${count} of ${total}`}
                  className="flex-1"
                  indicatorClassName="bg-warning"
                />
                <span className="w-10 shrink-0 text-right text-muted-foreground">
                  {count}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
