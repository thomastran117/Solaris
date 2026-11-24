import type { Metadata } from "next";
import RequireAuth from "../../components/auth/RequireAuth";
import { AdminShell } from "../../components/admin/AdminShell";

// Merchant and staff screens under /admin and /dashboard. The client guard
// requires a session; company capabilities and platform roles are enforced by
// the backend. The sidebar only hides links the user can't use.
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function MerchantLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <RequireAuth>
      <AdminShell>{children}</AdminShell>
    </RequireAuth>
  );
}
