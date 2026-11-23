import type { Metadata } from "next";
import RequireAuth from "../../components/auth/RequireAuth";

// Merchant and staff screens under /admin. The client guard requires a session;
// company capabilities and platform roles are enforced by the backend.
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function MerchantLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <RequireAuth>{children}</RequireAuth>;
}
