import type { ReactNode } from "react";
import { cn } from "../../lib/cn";

interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  /** Small line above the title, e.g. a breadcrumb or back link. */
  eyebrow?: ReactNode;
  /** Right-aligned page actions (usually Buttons). */
  actions?: ReactNode;
  className?: string;
}

/** The single `<h1>` block at the top of an app (account/admin) page. */
export function PageHeader({
  title,
  description,
  eyebrow,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <header
      className={cn(
        "flex flex-col gap-4 border-b pb-6 sm:flex-row sm:items-end sm:justify-between",
        className
      )}
    >
      <div className="min-w-0 space-y-1.5">
        {eyebrow && (
          <div className="text-sm text-muted-foreground">{eyebrow}</div>
        )}
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          {title}
        </h1>
        {description && (
          <p className="max-w-2xl text-sm text-muted-foreground">
            {description}
          </p>
        )}
      </div>
      {actions && (
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          {actions}
        </div>
      )}
    </header>
  );
}
