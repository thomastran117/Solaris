import { CheckCircle, ImageIcon } from "lucide-react";
import type { ReviewListFilters } from "../../api/reviews";

interface Props {
  filters: ReviewListFilters;
  onChange: (next: ReviewListFilters) => void;
}

type SortKey = "createdAt" | "rating" | "helpfulCount";

const SORT_OPTIONS: {
  value: SortKey;
  direction: "asc" | "desc";
  label: string;
}[] = [
  { value: "createdAt", direction: "desc", label: "Newest" },
  { value: "helpfulCount", direction: "desc", label: "Most helpful" },
  { value: "rating", direction: "desc", label: "Highest rated" },
  { value: "rating", direction: "asc", label: "Lowest rated" },
];

function chipClass(active: boolean) {
  return `inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
    active
      ? "border-primary/40 bg-primary/10 text-link"
      : "border-border bg-surface text-muted-foreground hover:border-input hover:text-foreground"
  }`;
}

export default function ReviewFilters({ filters, onChange }: Props) {
  const ratings = filters.rating ?? [];
  const sortKey = filters.sort ?? "createdAt";
  const sortDir = filters.direction ?? "desc";
  const activeSortLabel =
    SORT_OPTIONS.find((s) => s.value === sortKey && s.direction === sortDir)
      ?.label ?? "Newest";

  const toggleRating = (r: number) => {
    const next = ratings.includes(r)
      ? ratings.filter((x) => x !== r)
      : [...ratings, r];
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
    onChange({
      ...filters,
      hasMedia: filters.hasMedia ? undefined : true,
      page: 0,
    });
  };

  const onSortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const opt = SORT_OPTIONS[Number(e.target.value)];
    onChange({
      ...filters,
      sort: opt.value,
      direction: opt.direction,
      page: 0,
    });
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
      <button
        type="button"
        onClick={toggleVerified}
        className={chipClass(!!filters.verifiedOnly)}
      >
        <CheckCircle className="h-3.5 w-3.5" />
        Verified
      </button>
      <button
        type="button"
        onClick={togglePhotos}
        className={chipClass(!!filters.hasMedia)}
      >
        <ImageIcon className="h-3.5 w-3.5" />
        With photos
      </button>
      <div className="ml-auto flex items-center gap-2 text-xs text-muted-foreground">
        <label htmlFor="review-sort">Sort:</label>
        <select
          id="review-sort"
          value={SORT_OPTIONS.findIndex(
            (s) => s.value === sortKey && s.direction === sortDir
          )}
          onChange={onSortChange}
          aria-label={`Sort by ${activeSortLabel}`}
          className="rounded-full border border-border bg-surface px-3 py-1.5 text-xs text-foreground focus:border-ring focus:outline-none"
        >
          {SORT_OPTIONS.map((opt, i) => (
            <option
              key={`${opt.value}-${opt.direction}`}
              value={i}
              className="bg-surface"
            >
              {opt.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
