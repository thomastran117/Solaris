import type { ReactNode } from "react";
import FeedbackButton from "../feedback/FeedbackButton";
import { SiteFooter } from "./SiteFooter";
import { SiteHeader } from "./SiteHeader";

/** Header + main + footer chrome for storefront, auth and account routes. */
export function SiteShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <SiteHeader />
      {/* overflow-x-clip (not hidden) so sticky descendants keep working. */}
      <main
        id="main"
        tabIndex={-1}
        className="flex-1 overflow-x-clip focus:outline-none"
      >
        {children}
      </main>
      <SiteFooter />
      <FeedbackButton />
    </div>
  );
}
