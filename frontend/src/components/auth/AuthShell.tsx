import Link from "next/link";
import type { ReactNode } from "react";
import { ThemeToggle } from "../theme/ThemeToggle";
import { Wordmark } from "../layout/Wordmark";

/**
 * Focused layout for sign-in, sign-up and verification screens: logo and theme
 * toggle, a centred card, and legal links. No site navigation to pull the
 * visitor away mid-flow.
 */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="flex h-16 items-center justify-between px-4 sm:px-6">
        <Wordmark />
        <ThemeToggle />
      </header>
      <main
        id="main"
        tabIndex={-1}
        className="flex flex-1 items-start justify-center px-4 py-8 focus:outline-none sm:items-center sm:py-12"
      >
        <div className="w-full max-w-md">{children}</div>
      </main>
      <footer className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 px-4 py-6 text-sm text-muted-foreground">
        <span>© {new Date().getFullYear()} ShopWave</span>
        <Link href="/privacy" className="hover:text-foreground">
          Privacy
        </Link>
        <Link href="/terms-and-conditions" className="hover:text-foreground">
          Terms
        </Link>
      </footer>
    </div>
  );
}

/** The card that holds an auth form or status message. */
export function AuthCard({
  title,
  description,
  children,
  footer,
}: {
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  /** Below the card, e.g. "Don't have an account? Sign up". */
  footer?: ReactNode;
}) {
  return (
    <>
      <div className="rounded-xl border bg-surface p-6 shadow-xs sm:p-8">
        <div className="mb-6 space-y-1.5">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            {title}
          </h1>
          {description && (
            <p className="text-sm text-muted-foreground">{description}</p>
          )}
        </div>
        {children}
      </div>
      {footer && (
        <p className="mt-6 text-center text-sm text-muted-foreground">
          {footer}
        </p>
      )}
    </>
  );
}

/** Inline alert for form-level errors and notices. */
export function FormAlert({
  variant = "danger",
  children,
}: {
  variant?: "danger" | "info" | "success";
  children: ReactNode;
}) {
  const styles = {
    danger: "border-danger/30 bg-danger-subtle text-danger",
    info: "border-info/30 bg-info-subtle text-info",
    success: "border-success/30 bg-success-subtle text-success",
  }[variant];
  return (
    <div
      role={variant === "danger" ? "alert" : "status"}
      className={`rounded-lg border px-3 py-2.5 text-sm ${styles}`}
    >
      {children}
    </div>
  );
}

/** "or" divider between a form and social sign-in. */
export function AuthDivider() {
  return (
    <div className="my-6 flex items-center gap-3 text-xs text-muted-foreground">
      <span className="h-px flex-1 bg-border" />
      or
      <span className="h-px flex-1 bg-border" />
    </div>
  );
}

/* eslint-disable shopwave/no-raw-palette -- Google's brand colours must not follow the theme */
const GOOGLE = {
  blue: "#4285F4",
  green: "#34A853",
  yellow: "#FBBC05",
  red: "#EA4335",
};
/* eslint-enable shopwave/no-raw-palette */

export function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden className="size-4">
      <path
        fill={GOOGLE.blue}
        d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.5a5.6 5.6 0 0 1-2.4 3.6v3h3.9c2.2-2.1 3.5-5.1 3.5-8.8z"
      />
      <path
        fill={GOOGLE.green}
        d="M12 24c3.2 0 6-1.1 7.9-2.9l-3.9-3c-1.1.7-2.4 1.2-4 1.2-3.1 0-5.7-2.1-6.7-4.9h-4v3.1A12 12 0 0 0 12 24z"
      />
      <path
        fill={GOOGLE.yellow}
        d="M5.3 14.4a7.2 7.2 0 0 1 0-4.7V6.6h-4a12 12 0 0 0 0 10.9l4-3.1z"
      />
      <path
        fill={GOOGLE.red}
        d="M12 4.8c1.8 0 3.3.6 4.6 1.8l3.4-3.4A12 12 0 0 0 1.3 6.6l4 3.1c1-2.8 3.6-4.9 6.7-4.9z"
      />
    </svg>
  );
}
