import type { Metadata } from "next";
import RequireAuth from "../../components/auth/RequireAuth";
import { CheckoutShell } from "../../components/checkout/CheckoutShell";

// Focused checkout: logo, secure badge and a way back to the cart; no site
// navigation. Requires a session (proxy.ts redirects anonymous visitors).
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function CheckoutLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <CheckoutShell>
      <RequireAuth>{children}</RequireAuth>
    </CheckoutShell>
  );
}
