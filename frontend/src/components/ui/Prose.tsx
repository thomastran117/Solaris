import type { HTMLAttributes } from "react";
import { cn } from "../../lib/cn";

/** Long-form text (legal pages, descriptions) styled by @tailwindcss/typography. */
export function Prose({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "prose prose-zinc max-w-none dark:prose-invert",
        "prose-headings:tracking-tight prose-a:text-link prose-a:underline-offset-4",
        className
      )}
      {...props}
    />
  );
}
