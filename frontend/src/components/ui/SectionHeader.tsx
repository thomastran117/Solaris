import type { ReactNode } from "react";
import { cn } from "../../lib/cn";

interface SectionHeaderProps {
  title: ReactNode;
  /** Short label above the title. */
  kicker?: ReactNode;
  subtitle?: ReactNode;
  align?: "left" | "center";
  /** Right-aligned link or action (left-aligned layout only). */
  action?: ReactNode;
  /** Heading level; storefront sections under a page h1 use h2. */
  as?: "h1" | "h2" | "h3";
  className?: string;
}

/** Heading block for a storefront section. Replaces the legacy SectionTitle. */
export function SectionHeader({
  title,
  kicker,
  subtitle,
  align = "left",
  action,
  as: Heading = "h2",
  className,
}: SectionHeaderProps) {
  const centered = align === "center";
  return (
    <div
      className={cn(
        "flex gap-4",
        centered
          ? "flex-col items-center text-center"
          : "flex-col sm:flex-row sm:items-end sm:justify-between",
        className
      )}
    >
      <div className={cn("space-y-2", centered && "max-w-2xl")}>
        {kicker && <p className="text-sm font-medium text-link">{kicker}</p>}
        <Heading
          className={cn(
            "font-semibold tracking-tight text-foreground",
            Heading === "h1" ? "text-3xl sm:text-4xl" : "text-2xl sm:text-3xl"
          )}
        >
          {title}
        </Heading>
        {subtitle && (
          <p className="text-base text-muted-foreground">{subtitle}</p>
        )}
      </div>
      {action && !centered && <div className="shrink-0">{action}</div>}
    </div>
  );
}
