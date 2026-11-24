import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "../../lib/cn";
import { Button } from "./Button";

interface PaginationProps {
  /** Zero-based current page (matches the backend's `page` param). */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  /** Optional "x–y of z" summary. */
  totalItems?: number;
  pageSize?: number;
  disabled?: boolean;
  className?: string;
}

export function Pagination({
  page,
  totalPages,
  onPageChange,
  totalItems,
  pageSize,
  disabled,
  className,
}: PaginationProps) {
  if (totalPages <= 1 && totalItems === undefined) return null;
  const hasSummary = totalItems !== undefined && pageSize !== undefined;
  const from = hasSummary ? (totalItems === 0 ? 0 : page * pageSize + 1) : 0;
  const to = hasSummary ? Math.min((page + 1) * pageSize, totalItems) : 0;

  return (
    <nav
      aria-label="Pagination"
      className={cn(
        "flex items-center justify-between gap-4 text-sm",
        className
      )}
    >
      <p className="text-muted-foreground" aria-live="polite">
        {hasSummary
          ? `${from}–${to} of ${totalItems}`
          : `Page ${page + 1} of ${Math.max(totalPages, 1)}`}
      </p>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={disabled || page <= 0}
        >
          <ChevronLeft aria-hidden />
          Previous
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={disabled || page >= totalPages - 1}
        >
          Next
          <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  );
}
