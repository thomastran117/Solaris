import { cn } from "../../lib/cn";

interface ProgressProps {
  /** 0–100; values outside the range are clamped. */
  value: number;
  /** Accessible name, e.g. "Purchased items" or "Import progress". */
  label: string;
  /** Human-readable value for assistive tech, e.g. "3 of 5 purchased". */
  valueText?: string;
  /** Track classes (height, radius, background). */
  className?: string;
  /** Fill colour; defaults to the primary accent. */
  indicatorClassName?: string;
}

/**
 * Determinate progress bar: a `bg-muted` track with a `bg-primary` fill. Use it
 * for every progress or share indicator so the fill always contrasts with its
 * track in both themes.
 */
export function Progress({
  value,
  label,
  valueText,
  className,
  indicatorClassName,
}: ProgressProps) {
  const pct = Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : 0;
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(pct)}
      aria-valuetext={valueText}
      className={cn(
        "h-2 w-full overflow-hidden rounded-full bg-muted",
        className
      )}
    >
      <div
        className={cn(
          "h-full rounded-full bg-primary transition-[width] duration-500 motion-reduce:transition-none",
          indicatorClassName
        )}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
