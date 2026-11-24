import type { ReactNode } from "react";
import { AlertTriangle, RotateCw } from "lucide-react";
import { cn } from "../../lib/cn";
import { Button } from "./Button";

interface ErrorStateProps {
  title?: ReactNode;
  description?: ReactNode;
  /** Shows a "Try again" button, typically wired to a query's `refetch`. */
  onRetry?: () => void;
  retrying?: boolean;
  className?: string;
}

export function ErrorState({
  title = "Something went wrong",
  description = "We couldn't load this content. Check your connection and try again.",
  onRetry,
  retrying,
  className,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center justify-center rounded-xl border border-danger/30 bg-danger-subtle px-6 py-10 text-center",
        className
      )}
    >
      <AlertTriangle className="mb-3 size-6 text-danger" aria-hidden />
      <h3 className="text-base font-semibold text-foreground">{title}</h3>
      {description && (
        <p className="mt-1 max-w-sm text-sm text-muted-foreground">
          {description}
        </p>
      )}
      {onRetry && (
        <Button
          variant="outline"
          size="sm"
          className="mt-5"
          onClick={onRetry}
          loading={retrying}
        >
          {!retrying && <RotateCw aria-hidden />}
          Try again
        </Button>
      )}
    </div>
  );
}
