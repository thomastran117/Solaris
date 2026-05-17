import { CheckCircle, ImageIcon } from "lucide-react";
import type { ReviewListFilters } from "../../api/reviews";

interface Props {
  filters: ReviewListFilters;
  onChange: (next: ReviewListFilters) => void;
}

type SortKey = "createdAt" | "rating" | "helpfulCount";

const SORT_OPTIONS: { value: SortKey; direction: "asc" | "desc"; label: string }[] = [
  { value: "createdAt", direction: "desc", label: "Newest" },
  { value: "helpfulCount", direction: "desc", label: "Most helpful" },
  { value: "rating", direction: "desc", label: "Highest rated" },
  { value: "rating", direction: "asc", label: "Lowest rated" },
];

function chipClass(active: boolean) {
  return `inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
    active
      ? "border-blue-400/40 bg-blue-500/15 text-blue-300"
      : "border-white/15 bg-white/[0.04] text-white/70 hover:border-white/30 hover:text-white"
  }`;
}

export default function ReviewFilters({ filters, onChange }: Props) {
  const ratings = filters.rating ?? [];
  const sortKey = filters.sort ?? "createdAt";
  const sortDir = filters.direction ?? "desc";
  const activeSortLabel =
    SORT_OPTIONS.find((s) => s.value === sortKey && s.direction === sortDir)?.label ?? "Newest";

  const toggleRating = (r: number) => {
    const next = ratings.includes(r) ? ratings.filter((x) => x !== r) : [...ratings, r];
    onChange({ ...filters, rating: next.length ? next : undefined, page: 0 });
  };

  const toggleVerified = () => {
    onChange({
      ...filters,
      verifiedOnly: filters.verifiedOnly ? undefined : true,
      page: 0,
    });
  };

  const togglePhotos = () => {
    onChange({ ...filters, hasMedia: filters.hasMedia ? undefined : true, page: 0 });
  };

  const onSortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const opt = SORT_OPTIONS[Number(e.target.value)];
    onChange({ ...filters, sort: opt.value, direction: opt.direction, page: 0 });
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      {[5, 4, 3, 2, 1].map((s) => (
        <button
          key={s}
          type="button"
          onClick={() => toggleRating(s)}
          className={chipClass(ratings.includes(s))}
        >
          {s}★
        </button>
      ))}
      <button type="button" onClick={toggleVerified} className={chipClass(!!filters.verifiedOnly)}>
        <CheckCircle className="h-3.5 w-3.5" />
        Verified
      </button>
      <button type="button" onClick={togglePhotos} className={chipClass(!!filters.hasMedia)}>
        <ImageIcon className="h-3.5 w-3.5" />
        With photos
      </button>
      <div className="ml-auto flex items-center gap-2 text-xs text-white/65">
        <label htmlFor="review-sort">Sort:</label>
        <select
          id="review-sort"
          value={SORT_OPTIONS.findIndex((s) => s.value === sortKey && s.direction === sortDir)}
          onChange={onSortChange}
          aria-label={`Sort by ${activeSortLabel}`}
          className="rounded-full border border-white/15 bg-white/[0.04] px-3 py-1.5 text-xs text-white focus:border-blue-400/40 focus:outline-none"
        >
          {SORT_OPTIONS.map((opt, i) => (
            <option key={`${opt.value}-${opt.direction}`} value={i} className="bg-slate-900">
              {opt.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
