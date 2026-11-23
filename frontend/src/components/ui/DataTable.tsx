import type {
  HTMLAttributes,
  ReactNode,
  TdHTMLAttributes,
  ThHTMLAttributes,
} from "react";
import { cn } from "../../lib/cn";
import { Skeleton } from "./Skeleton";

/*
 * Table building blocks for admin and account lists. These are styling shells
 * around native table elements; data fetching, sorting and pagination stay in
 * the view (TanStack Query + URL params).
 */

export function TableContainer({
  className,
  toolbar,
  footer,
  children,
}: {
  className?: string;
  /** Filters/search above the table. */
  toolbar?: ReactNode;
  /** Usually <Pagination>. */
  footer?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div
      className={cn(
        "overflow-hidden rounded-xl border bg-surface shadow-xs",
        className
      )}
    >
      {toolbar && (
        <div className="flex flex-wrap items-center gap-2 border-b p-3">
          {toolbar}
        </div>
      )}
      <div className="overflow-x-auto">{children}</div>
      {footer && <div className="border-t px-4 py-3">{footer}</div>}
    </div>
  );
}

export function Table({
  className,
  ...props
}: HTMLAttributes<HTMLTableElement>) {
  return (
    <table
      className={cn("w-full caption-bottom border-collapse text-sm", className)}
      {...props}
    />
  );
}

export function THead({
  className,
  ...props
}: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn("bg-muted/50", className)} {...props} />;
}

export function TBody({
  className,
  ...props
}: HTMLAttributes<HTMLTableSectionElement>) {
  return (
    <tbody className={cn("[&_tr:last-child]:border-0", className)} {...props} />
  );
}

export function TRow({
  className,
  ...props
}: HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr
      className={cn(
        "border-b transition-colors hover:bg-muted/40 data-[selected=true]:bg-muted",
        className
      )}
      {...props}
    />
  );
}

export function TH({
  className,
  scope = "col",
  ...props
}: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      scope={scope}
      className={cn(
        "h-10 whitespace-nowrap px-4 text-left align-middle text-xs font-medium uppercase tracking-wide text-muted-foreground",
        className
      )}
      {...props}
    />
  );
}

export function TD({
  className,
  ...props
}: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn("px-4 py-3 align-middle", className)} {...props} />;
}

/** Skeleton rows while the first page loads. */
export function TableLoadingRows({
  columns,
  rows = 5,
}: {
  columns: number;
  rows?: number;
}) {
  return (
    <>
      {Array.from({ length: rows }, (_, r) => (
        <tr key={r} className="border-b">
          {Array.from({ length: columns }, (_, c) => (
            <td key={c} className="px-4 py-3">
              <Skeleton className="h-4 w-full max-w-40" />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}

/** A single full-width row for empty or error content. */
export function TableMessageRow({
  columns,
  children,
}: {
  columns: number;
  children: ReactNode;
}) {
  return (
    <tr>
      <td colSpan={columns} className="px-4 py-10 text-center">
        {children}
      </td>
    </tr>
  );
}
