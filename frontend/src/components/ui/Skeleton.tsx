import type { HTMLAttributes } from "react";
import { cn } from "../../lib/cn";

/** Placeholder block for loading content. Size it with className. */
export function Skeleton({
  className,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden
      className={cn(
        "animate-pulse rounded-md bg-muted motion-reduce:animate-none",
        className
      )}
      {...props}
    />
  );
}
