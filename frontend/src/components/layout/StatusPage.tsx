import type { ReactNode } from "react";

interface StatusPageProps {
  /** Short code or label above the title, e.g. "404". */
  code?: string;
  title: ReactNode;
  description?: ReactNode;
  /** Buttons/links offering a way forward. */
  actions?: ReactNode;
  /** Set for error boundaries so the message is announced. */
  alert?: boolean;
}

/** Full-height message for not-found and error boundaries. */
export function StatusPage({
  code,
  title,
  description,
  actions,
  alert,
}: StatusPageProps) {
  return (
    <div
      role={alert ? "alert" : undefined}
      className="flex min-h-[60vh] items-center justify-center px-4 py-24"
    >
      <div className="max-w-md text-center">
        {code && (
          <p className="text-sm font-semibold tabular-nums text-link">{code}</p>
        )}
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-foreground">
          {title}
        </h1>
        {description && (
          <p className="mt-3 text-base text-muted-foreground">{description}</p>
        )}
        {actions && (
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            {actions}
          </div>
        )}
      </div>
    </div>
  );
}
