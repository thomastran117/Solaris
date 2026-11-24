import type { Metadata } from "next";
import RequireAuth from "../../components/auth/RequireAuth";
import { AccountShell } from "../../components/layout/AccountShell";
import { SiteShell } from "../../components/layout/SiteShell";

// Signed-in customer screens. Their HTML is a shell with no private data; content
// loads in the browser after the client guard confirms a session.
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function AccountLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <SiteShell>
      <AccountShell>
        <RequireAuth>{children}</RequireAuth>
      </AccountShell>
    </SiteShell>
  );
}
