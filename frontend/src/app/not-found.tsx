import type { Metadata } from "next";
import Link from "next/link";
import { SiteShell } from "../components/layout/SiteShell";
import { StatusPage } from "../components/layout/StatusPage";
import { Button } from "../components/ui/Button";

export const metadata: Metadata = {
  title: "Page not found",
  robots: { index: false },
};

// Unmatched URLs render outside every route group, so this page brings its own
// chrome.
export default function NotFound() {
  return (
    <SiteShell>
      <StatusPage
        code="404"
        title="Page not found"
        description="The page you are looking for does not exist or has moved."
        actions={
          <>
            <Button asChild>
              <Link href="/">Go home</Link>
            </Button>
            <Button asChild variant="outline">
              <Link href="/browse">Browse products</Link>
            </Button>
          </>
        }
      />
    </SiteShell>
  );
}
