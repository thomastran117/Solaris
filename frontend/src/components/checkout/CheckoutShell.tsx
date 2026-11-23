import Link from "next/link";
import type { ReactNode } from "react";
import { ArrowLeft, Lock } from "lucide-react";
import { ThemeToggle } from "../theme/ThemeToggle";
import { Wordmark } from "../layout/Wordmark";

/** Distraction-free chrome for /checkout. */
export function CheckoutShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="border-b bg-surface">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-4">
            <Wordmark />
            <span className="hidden items-center gap-1.5 text-sm text-muted-foreground sm:flex">
              <Lock className="size-4" aria-hidden />
              Secure checkout
            </span>
          </div>
          <div className="flex items-center gap-2">
            <Link
              href="/cart"
              className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <ArrowLeft className="size-4" aria-hidden />
              Back to cart
            </Link>
            <ThemeToggle />
          </div>
        </div>
      </header>
      <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
        {children}
      </main>
    </div>
  );
}
