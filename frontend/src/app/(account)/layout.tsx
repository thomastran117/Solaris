import type { Metadata } from "next";
import RequireAuth from "../../components/auth/RequireAuth";

// Signed-in customer screens. Their HTML is a shell with no private data; content
// loads in the browser after the client guard confirms a session.
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function AccountLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <RequireAuth>{children}</RequireAuth>;
}
